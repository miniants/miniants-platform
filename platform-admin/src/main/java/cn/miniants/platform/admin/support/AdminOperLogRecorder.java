package cn.miniants.platform.admin.support;

import cn.miniants.platform.admin.dto.OperLogRecord;
import cn.miniants.platform.admin.service.OperLogAdminService;
import cn.miniants.platform.security.CurrentUser;
import cn.miniants.platform.security.OperLogEntry;
import cn.miniants.platform.security.OperLogRecorder;
import org.slf4j.MDC;

public class AdminOperLogRecorder implements OperLogRecorder {

    private final OperLogAdminService operLogAdminService;

    public AdminOperLogRecorder(OperLogAdminService operLogAdminService) {
        this.operLogAdminService = operLogAdminService;
    }

    @Override
    public void record(OperLogEntry entry) {
        if (entry == null) {
            return;
        }
        OperLogRecord body = new OperLogRecord();
        body.setTitle(entry.title());
        body.setEventType(entry.eventType());
        body.setTraceId(MDC.get("traceId"));
        body.setRepeatCount(1);
        body.setHttpMethod(entry.httpMethod());
        body.setHttpStatus(entry.httpStatus());
        body.setRequestUri(entry.requestUri());
        body.setRequestParam(entry.requestParam());
        body.setSuccess(entry.success() ? 1 : 0);
        body.setErrorMessage(entry.errorMessage());
        CurrentUser user = CurrentUser.find();
        body.setOperatorId(user == null ? entry.operatorId() : user.userId());
        body.setOperatorName(user == null ? entry.operatorName()
                : (user.username() == null || user.username().isBlank() ? entry.operatorName() : user.username()));
        body.setRequestIp(entry.requestIp());
        body.setCostMs(entry.costMs());
        operLogAdminService.record(body);
    }
}
