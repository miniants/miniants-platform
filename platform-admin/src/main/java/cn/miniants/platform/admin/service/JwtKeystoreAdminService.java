package cn.miniants.platform.admin.service;

import cn.miniants.platform.admin.dto.JwtKeystoreVo;
import cn.miniants.platform.admin.dto.JwtWrapSettingsVo;
import cn.miniants.platform.admin.dto.PageResult;

import java.util.List;

public interface JwtKeystoreAdminService {

    PageResult<JwtKeystoreVo> page(Long current, Long size, String filter, String order);

    List<JwtKeystoreVo> list();

    JwtWrapSettingsVo wrapSettings();

    JwtWrapSettingsVo saveWrapSource(String source);

    JwtKeystoreVo generate();

    JwtKeystoreVo activate(String kid);

    JwtKeystoreVo retire(String kid);
}
