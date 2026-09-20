package cn.miniants.platform.admin.service;

import cn.miniants.platform.admin.dto.PageResult;
import cn.miniants.platform.admin.dto.RoleSave;
import cn.miniants.platform.admin.dto.RoleVo;

public interface RoleAdminService {

    PageResult<RoleVo> page(Long current, Long size, String filter, String order);

    RoleVo get(Long id);

    RoleVo save(RoleSave body);

    void delete(Long id);
}
