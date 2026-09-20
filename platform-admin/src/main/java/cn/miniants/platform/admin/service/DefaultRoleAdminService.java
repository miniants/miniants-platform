package cn.miniants.platform.admin.service;

import cn.miniants.platform.admin.dto.DataScopeDto;
import cn.miniants.platform.admin.dto.PageResult;
import cn.miniants.platform.admin.dto.RoleSave;
import cn.miniants.platform.admin.dto.RoleVo;
import cn.miniants.platform.admin.entity.Role;
import cn.miniants.platform.admin.entity.RoleDataScope;
import cn.miniants.platform.admin.entity.RoleResource;
import cn.miniants.platform.admin.entity.UserRole;
import cn.miniants.platform.admin.mapper.ResourceMapper;
import cn.miniants.platform.admin.mapper.RoleDataScopeMapper;
import cn.miniants.platform.admin.mapper.RoleMapper;
import cn.miniants.platform.admin.mapper.RoleResourceMapper;
import cn.miniants.platform.admin.mapper.UserRoleMapper;
import cn.miniants.platform.admin.support.AdminReferenceValidator;
import cn.miniants.platform.admin.support.EntityPages;
import cn.miniants.platform.admin.support.PageQueries;
import cn.miniants.platform.admin.support.PersistIds;
import cn.miniants.platform.core.error.PlatformException;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

public class DefaultRoleAdminService implements RoleAdminService {

    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleResourceMapper roleResourceMapper;
    private final RoleDataScopeMapper roleDataScopeMapper;
    private final ResourceMapper resourceMapper;

    public DefaultRoleAdminService(RoleMapper roleMapper, UserRoleMapper userRoleMapper,
            RoleResourceMapper roleResourceMapper, RoleDataScopeMapper roleDataScopeMapper,
            ResourceMapper resourceMapper) {
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleResourceMapper = roleResourceMapper;
        this.roleDataScopeMapper = roleDataScopeMapper;
        this.resourceMapper = resourceMapper;
    }

    @Override
    public PageResult<RoleVo> page(Long current, Long size, String filter, String order) {
        Page<Role> page = roleMapper.selectPage(
                new Page<>(PageQueries.current(current), PageQueries.size(size)),
                EntityPages.wrapper(Role.class, filter, order));
        List<Long> ids = page.getRecords().stream().map(Role::getId).toList();
        Map<Long, List<Long>> resources = resourceIdsByRole(ids);
        Map<Long, RoleDataScope> scopes = dataScopesByRole(ids);
        List<RoleVo> records = page.getRecords().stream()
                .map(role -> toVo(role, resources.getOrDefault(role.getId(), List.of()),
                        scopes.get(role.getId())))
                .toList();
        return PageResult.of(page, records);
    }

    @Override
    public RoleVo get(Long id) {
        Role role = requireRole(id);
        return toVo(role, resourceIdsByRole(List.of(id)).getOrDefault(id, List.of()),
                roleDataScopeMapper.selectById(id));
    }

    @Override
    @Transactional
    public RoleVo save(RoleSave body) {
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
        requireRole(id);
        userRoleMapper.delete(Wrappers.<UserRole>lambdaQuery().eq(UserRole::getRoleId, id));
        roleResourceMapper.delete(Wrappers.<RoleResource>lambdaQuery().eq(RoleResource::getRoleId, id));
        roleDataScopeMapper.deleteById(id);
        roleMapper.deleteById(id);
    }

    private RoleVo create(RoleSave body) {
        Role role = new Role();
        role.setCode(requireText(body.getCode(), "角色编码不能为空"));
        role.setName(requireText(body.getName(), "角色名称不能为空"));
        role.setRemark(blankToNull(body.getRemark()));
        role.setSortNo(body.getSortNo() == null ? 0 : body.getSortNo());
        role.setStatus(body.getStatus() == null ? 1 : body.getStatus());
        try {
            roleMapper.insert(role);
        } catch (DuplicateKeyException ex) {
            throw new PlatformException("角色编码已存在");
        }
        replaceResources(role.getId(), body.getResourceIds());
        saveDataScope(role.getId(), body.getDataScope());
        return get(role.getId());
    }

    private RoleVo update(RoleSave body) {
        Role existing = requireRole(body.getId());
        if (body.getCode() != null) {
            existing.setCode(requireText(body.getCode(), "角色编码不能为空"));
        }
        if (body.getName() != null) {
            existing.setName(requireText(body.getName(), "角色名称不能为空"));
        }
        if (body.getRemark() != null) {
            existing.setRemark(blankToNull(body.getRemark()));
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
            if (roleMapper.updateById(existing) == 0) {
                throw new PlatformException("角色不存在或已被修改");
            }
        } catch (DuplicateKeyException ex) {
            throw new PlatformException("角色编码已存在");
        }
        if (body.getResourceIds() != null) {
            replaceResources(existing.getId(), body.getResourceIds());
        }
        saveDataScope(existing.getId(), body.getDataScope());
        return get(existing.getId());
    }

    /** 不传就不动已有配置；传了整体覆盖。 */
    private void saveDataScope(Long roleId, DataScopeDto dataScope) {
        if (dataScope == null) {
            return;
        }
        RoleDataScope row = new RoleDataScope();
        row.setRoleId(roleId);
        row.setScopeType(dataScope.getScopeType() == null
                ? RoleDataScope.SCOPE_USER_BOUND : dataScope.getScopeType());
        row.setScopeValue(dataScope.getScopeValue());
        if (roleDataScopeMapper.updateById(row) == 0) {
            roleDataScopeMapper.insert(row);
        }
    }

    private Map<Long, RoleDataScope> dataScopesByRole(List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, RoleDataScope> byRole = new LinkedHashMap<>();
        for (RoleDataScope row : roleDataScopeMapper.selectByIds(roleIds)) {
            byRole.put(row.getRoleId(), row);
        }
        return byRole;
    }

    private void replaceResources(Long roleId, List<Long> resourceIds) {
        AdminReferenceValidator.requireResources(resourceMapper, resourceIds);
        roleResourceMapper.delete(Wrappers.<RoleResource>lambdaQuery().eq(RoleResource::getRoleId, roleId));
        if (resourceIds == null || resourceIds.isEmpty()) {
            return;
        }
        List<Long> unique = resourceIds.stream().filter(Objects::nonNull).distinct().toList();
        for (Long resourceId : unique) {
            RoleResource link = new RoleResource();
            link.setRoleId(roleId);
            link.setResourceId(resourceId);
            link.setCreateTime(LocalDateTime.now());
            roleResourceMapper.insert(link);
        }
    }

    private Map<Long, List<Long>> resourceIdsByRole(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Map.of();
        }
        List<RoleResource> links = roleResourceMapper.selectList(
                Wrappers.<RoleResource>lambdaQuery().in(RoleResource::getRoleId, roleIds));
        Map<Long, List<Long>> grouped = new LinkedHashMap<>();
        for (RoleResource link : links) {
            grouped.computeIfAbsent(link.getRoleId(), key -> new ArrayList<>()).add(link.getResourceId());
        }
        return grouped;
    }

    private Role requireRole(Long id) {
        if (!PersistIds.persisted(id)) {
            throw new PlatformException("角色不存在");
        }
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new PlatformException("角色不存在");
        }
        return role;
    }

    private static RoleVo toVo(Role role, List<Long> resourceIds, RoleDataScope dataScope) {
        RoleVo vo = new RoleVo();
        if (dataScope != null) {
            DataScopeDto scope = new DataScopeDto();
            scope.setScopeType(dataScope.getScopeType());
            scope.setScopeValue(dataScope.getScopeValue());
            vo.setDataScope(scope);
        }
        vo.setId(role.getId());
        vo.setCode(role.getCode());
        vo.setName(role.getName());
        vo.setRemark(role.getRemark());
        vo.setSortNo(role.getSortNo());
        vo.setStatus(role.getStatus());
        vo.setResourceIds(resourceIds == null ? List.of() : List.copyOf(resourceIds));
        vo.setVersion(role.getVersion());
        vo.setCreateBy(role.getCreateBy());
        vo.setUpdateBy(role.getUpdateBy());
        vo.setCreateTime(role.getCreateTime());
        vo.setUpdateTime(role.getUpdateTime());
        return vo;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new PlatformException(message);
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
