package cn.miniants.platform.admin.service;

import cn.miniants.platform.admin.dto.PageResult;
import cn.miniants.platform.admin.dto.UserSave;
import cn.miniants.platform.admin.dto.UserVo;

public interface UserAdminService {

    PageResult<UserVo> page(Long current, Long size, String filter, String order, Long roleId);

    UserVo get(Long id);

    UserVo save(UserSave body);

    void delete(Long id);
}
