package cn.miniants.platform.admin.service;

import cn.miniants.platform.admin.dto.PageResult;
import cn.miniants.platform.admin.dto.ResourceMetaDto;
import cn.miniants.platform.admin.dto.ResourceSave;
import cn.miniants.platform.admin.dto.ResourceTreeNode;
import cn.miniants.platform.admin.dto.ResourceVo;
import cn.miniants.platform.admin.entity.Resource;
import cn.miniants.platform.admin.entity.ResourceMeta;
import cn.miniants.platform.admin.entity.RoleResource;
import cn.miniants.platform.admin.mapper.ResourceMapper;
import cn.miniants.platform.admin.mapper.ResourceMetaMapper;
import cn.miniants.platform.admin.mapper.RoleResourceMapper;
import cn.miniants.platform.admin.support.EntityPages;
import cn.miniants.platform.admin.support.PageQueries;
import cn.miniants.platform.admin.support.PersistIds;
import cn.miniants.platform.admin.support.ResourceMetas;
import cn.miniants.platform.admin.support.ResourceTrees;
import cn.miniants.platform.core.error.PlatformException;
import cn.miniants.platform.security.CurrentUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DefaultResourceAdminService implements ResourceAdminService {

    private final ResourceMapper resourceMapper;
    private final RoleResourceMapper roleResourceMapper;
    private final ResourceMetaMapper resourceMetaMapper;

    public DefaultResourceAdminService(ResourceMapper resourceMapper, RoleResourceMapper roleResourceMapper,
            ResourceMetaMapper resourceMetaMapper) {
        this.resourceMapper = resourceMapper;
        this.roleResourceMapper = roleResourceMapper;
        this.resourceMetaMapper = resourceMetaMapper;
    }

    @Override
    public PageResult<ResourceVo> page(Long current, Long size, String filter, String order) {
        Page<Resource> page = resourceMapper.selectPage(
                new Page<>(PageQueries.current(current), PageQueries.size(size)),
                EntityPages.wrapper(Resource.class, filter, order));
        Map<Long, ResourceMeta> metas = metasOf(page.getRecords().stream().map(Resource::getId).toList());
        return PageResult.of(page, page.getRecords().stream()
                .map(row -> toVo(row, metas.get(row.getId()))).toList());
    }

    @Override
    public List<ResourceTreeNode> tree() {
        List<Resource> rows = resourceMapper.selectList(
                Wrappers.<Resource>lambdaQuery().orderByAsc(Resource::getSortNo).orderByAsc(Resource::getId));
        return ResourceTrees.build(rows, metasOf(rows.stream().map(Resource::getId).toList()));
    }

    @Override
    public List<ResourceTreeNode> mine() {
        CurrentUser user = CurrentUser.require();
        return ResourceTrees.visible(tree(), user.permissions(), user.sysAdmin());
    }

    @Override
    public ResourceVo get(Long id) {
        return toVo(requireResource(id), resourceMetaMapper.selectById(id));
    }

    @Override
    @Transactional
    public ResourceVo save(ResourceSave body) {
        if (body == null) {
            throw new PlatformException("请求参数不正确");
        }
        if (PersistIds.persisted(body.getId())) {
            return update(body);
        }
        return create(body);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireResource(id);
        Long children = resourceMapper.selectCount(
                Wrappers.<Resource>lambdaQuery().eq(Resource::getParentId, id));
        if (children != null && children > 0) {
            throw new PlatformException("请先删除子资源");
        }
        roleResourceMapper.delete(Wrappers.<RoleResource>lambdaQuery().eq(RoleResource::getResourceId, id));
        // meta 行留着：主表是逻辑删除，物理删掉路由配置就没法跟着一起恢复了
        resourceMapper.deleteById(id);
    }

    private ResourceVo create(ResourceSave body) {
        Resource resource = new Resource();
        resource.setCode(requireText(body.getCode(), "资源编码不能为空"));
        resource.setName(requireText(body.getName(), "资源名称不能为空"));
        resource.setParentId(body.getParentId());
        resource.setType(body.getType() == null ? 1 : body.getType());
        resource.setPath(blankToNull(body.getPath()));
        resource.setSortNo(body.getSortNo() == null ? 0 : body.getSortNo());
        resource.setStatus(body.getStatus() == null ? 1 : body.getStatus());
        try {
            resourceMapper.insert(resource);
        } catch (DuplicateKeyException ex) {
            throw new PlatformException("资源编码已存在");
        }
        saveMeta(resource.getId(), body.getMeta());
        return get(resource.getId());
    }

    private ResourceVo update(ResourceSave body) {
        Resource existing = requireResource(body.getId());
        if (body.getCode() != null) {
            existing.setCode(requireText(body.getCode(), "资源编码不能为空"));
        }
        if (body.getName() != null) {
            existing.setName(requireText(body.getName(), "资源名称不能为空"));
        }
        if (body.getParentId() != null) {
            existing.setParentId(body.getParentId());
        }
        if (body.getType() != null) {
            existing.setType(body.getType());
        }
        if (body.getPath() != null) {
            existing.setPath(blankToNull(body.getPath()));
        }
        if (body.getSortNo() != null) {
            existing.setSortNo(body.getSortNo());
        }
        if (body.getStatus() != null) {
            existing.setStatus(body.getStatus());
        }
        if (body.getVersion() != null) {
            existing.setVersion(body.getVersion());
        }
        try {
            if (resourceMapper.updateById(existing) == 0) {
                throw new PlatformException("资源不存在或已被修改");
            }
        } catch (DuplicateKeyException ex) {
            throw new PlatformException("资源编码已存在");
        }
        saveMeta(existing.getId(), body.getMeta());
        return get(existing.getId());
    }

    /** 不传 meta 就不动已有配置；传了整体覆盖，缺省的布尔位按 false 落库。 */
    private void saveMeta(Long resourceId, ResourceMetaDto meta) {
        if (meta == null) {
            return;
        }
        ResourceMeta row = ResourceMetas.toEntity(resourceId, meta);
        if (resourceMetaMapper.updateById(row) == 0) {
            resourceMetaMapper.insert(row);
        }
    }

    private Map<Long, ResourceMeta> metasOf(List<Long> resourceIds) {
        if (resourceIds.isEmpty()) {
            return Map.of();
        }
        return resourceMetaMapper.selectByIds(resourceIds).stream()
                .collect(Collectors.toMap(ResourceMeta::getResourceId, row -> row));
    }

    private Resource requireResource(Long id) {
        if (!PersistIds.persisted(id)) {
            throw new PlatformException("资源不存在");
        }
        Resource resource = resourceMapper.selectById(id);
        if (resource == null) {
            throw new PlatformException("资源不存在");
        }
        return resource;
    }

    private static ResourceVo toVo(Resource resource, ResourceMeta meta) {
        ResourceVo vo = new ResourceVo();
        vo.setMeta(ResourceMetas.toDto(meta));
        vo.setId(resource.getId());
        vo.setParentId(resource.getParentId());
        vo.setCode(resource.getCode());
        vo.setName(resource.getName());
        vo.setType(resource.getType());
        vo.setPath(resource.getPath());
        vo.setSortNo(resource.getSortNo());
        vo.setStatus(resource.getStatus());
        vo.setVersion(resource.getVersion());
        vo.setCreateTime(resource.getCreateTime());
        vo.setUpdateTime(resource.getUpdateTime());
        return vo;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new PlatformException(message);
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
