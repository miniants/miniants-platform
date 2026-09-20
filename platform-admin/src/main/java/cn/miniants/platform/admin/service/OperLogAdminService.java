package cn.miniants.platform.admin.service;

import cn.miniants.platform.admin.dto.OperLogRecord;
import cn.miniants.platform.admin.dto.OperLogVo;
import cn.miniants.platform.admin.dto.PageResult;

public interface OperLogAdminService {

    PageResult<OperLogVo> page(Long current, Long size, String filter, String order, boolean collapse);

    OperLogVo get(Long id);

    OperLogVo record(OperLogRecord body);
}
