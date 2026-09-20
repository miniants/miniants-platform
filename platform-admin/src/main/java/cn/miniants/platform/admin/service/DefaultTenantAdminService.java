package cn.miniants.platform.admin.service;

import cn.miniants.platform.admin.dto.PageResult;
import cn.miniants.platform.admin.dto.TenantSave;
import cn.miniants.platform.admin.dto.TenantVo;
import cn.miniants.platform.admin.entity.Tenant;
import cn.miniants.platform.admin.mapper.TenantMapper;
import cn.miniants.platform.admin.support.EntityPages;
import cn.miniants.platform.admin.support.PageQueries;
import cn.miniants.platform.admin.support.PersistIds;
import cn.miniants.platform.core.error.PlatformException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

public class DefaultTenantAdminService implements TenantAdminService {

    private final TenantMapper tenantMapper;

    public DefaultTenantAdminService(TenantMapper tenantMapper) {
        this.tenantMapper = tenantMapper;
    }

    @Override
    public PageResult<TenantVo> page(Long current, Long size, String filter, String order) {
        Page<Tenant> page = tenantMapper.selectPage(
                new Page<>(PageQueries.current(current), PageQueries.size(size)),
                EntityPages.wrapper(Tenant.class, filter, order));
        return PageResult.of(page, page.getRecords().stream().map(DefaultTenantAdminService::toVo).toList());
    }

    @Override
    public TenantVo get(Long id) {
        return toVo(requireTenant(id));
    }

    @Override
    @Transactional
    public TenantVo save(TenantSave body) {
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
        requireTenant(id);
        tenantMapper.deleteById(id);
    }

    private TenantVo create(TenantSave body) {
        Tenant tenant = new Tenant();
        tenant.setCode(requireText(body.getCode(), "租户编码不能为空"));
        tenant.setName(requireText(body.getName(), "租户名称不能为空"));
        tenant.setStatus(body.getStatus() == null ? 1 : body.getStatus());
        try {
            tenantMapper.insert(tenant);
        } catch (DuplicateKeyException ex) {
            throw new PlatformException("租户编码已存在");
        }
        return get(tenant.getId());
    }

    private TenantVo update(TenantSave body) {
        Tenant existing = requireTenant(body.getId());
        if (body.getCode() != null) {
            existing.setCode(requireText(body.getCode(), "租户编码不能为空"));
        }
        if (body.getName() != null) {
            existing.setName(requireText(body.getName(), "租户名称不能为空"));
        }
        if (body.getStatus() != null) {
            existing.setStatus(body.getStatus());
        }
        if (body.getVersion() != null) {
            existing.setVersion(body.getVersion());
        }
        try {
            if (tenantMapper.updateById(existing) == 0) {
                throw new PlatformException("租户不存在或已被修改");
            }
        } catch (DuplicateKeyException ex) {
            throw new PlatformException("租户编码已存在");
        }
        return get(existing.getId());
    }

    private Tenant requireTenant(Long id) {
        if (!PersistIds.persisted(id)) {
            throw new PlatformException("租户不存在");
        }
        Tenant tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            throw new PlatformException("租户不存在");
        }
        return tenant;
    }

    private static TenantVo toVo(Tenant tenant) {
        TenantVo vo = new TenantVo();
        vo.setId(tenant.getId());
        vo.setCode(tenant.getCode());
        vo.setName(tenant.getName());
        vo.setStatus(tenant.getStatus());
        vo.setVersion(tenant.getVersion());
        vo.setCreateTime(tenant.getCreateTime());
        vo.setUpdateTime(tenant.getUpdateTime());
        return vo;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new PlatformException(message);
        }
        return value.trim();
    }
}
