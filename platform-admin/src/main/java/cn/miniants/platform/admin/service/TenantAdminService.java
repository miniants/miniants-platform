package cn.miniants.platform.admin.service;

import cn.miniants.platform.admin.dto.PageResult;
import cn.miniants.platform.admin.dto.TenantSave;
import cn.miniants.platform.admin.dto.TenantVo;

public interface TenantAdminService {

    PageResult<TenantVo> page(Long current, Long size, String filter, String order);

    TenantVo get(Long id);

    TenantVo save(TenantSave body);

    void delete(Long id);
}
