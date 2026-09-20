package cn.miniants.platform.admin.service;

import cn.miniants.platform.admin.dto.ConfigSave;
import cn.miniants.platform.admin.dto.ConfigVo;
import cn.miniants.platform.admin.dto.PageResult;

public interface ConfigAdminService {

    PageResult<ConfigVo> page(Long current, Long size, String filter, String order);

    ConfigVo get(Long id);

    ConfigVo save(ConfigSave body);

    void delete(Long id);
}
