package cn.miniants.platform.admin.service;

import cn.miniants.platform.admin.dto.OauthClientSave;
import cn.miniants.platform.admin.dto.OauthClientVo;
import cn.miniants.platform.admin.dto.PageResult;

public interface OauthClientAdminService {

    PageResult<OauthClientVo> page(Long current, Long size, String filter, String order);

    OauthClientVo get(Long id);

    OauthClientVo save(OauthClientSave body);

    OauthClientVo resetSecret(Long id);

    void delete(Long id);
}
