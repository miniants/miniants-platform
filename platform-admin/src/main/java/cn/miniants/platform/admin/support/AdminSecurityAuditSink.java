package cn.miniants.platform.admin.support;

import cn.miniants.platform.security.AccessAuth;
import cn.miniants.platform.security.Actor;
import cn.miniants.platform.security.AuditActors;
import cn.miniants.platform.security.CurrentUser;
import cn.miniants.platform.security.LoginAttempt;
import cn.miniants.platform.security.OperLogEntry;
import cn.miniants.platform.security.OperLogEventTypes;
import cn.miniants.platform.security.OperLogRecorder;
import cn.miniants.platform.security.SecurityAuditSink;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;

/**
 * 高价值安全事件落 {@code sys_oper_log}：登录、鉴权拒绝、未分类。
 * 普通 ok/permit 只盖 {@link AccessAuth}，不落库。
 */
public class AdminSecurityAuditSink implements SecurityAuditSink {

    private static final int ERROR_MAX = 512;
    private static final int SUMMARY_MAX = 2048;

    private final ObjectProvider<OperLogRecorder> recorders;
    private final OperLogRecorder recorder;

    public AdminSecurityAuditSink(OperLogRecorder recorder) {
        this.recorders = null;
        this.recorder = recorder;
    }

    public AdminSecurityAuditSink(ObjectProvider<OperLogRecorder> recorders) {
        this.recorders = recorders;
        this.recorder = null;
    }

    @Override
    public void record(HttpServletRequest request, String reason, Actor actor) {
        AccessAuth.stamp(request, AccessAuth.authFromReason(reason), reason, actor);
        String eventType = persistEventType(reason);
        if (eventType == null) {
            return;
        }
        boolean deny = AccessAuth.DENY.equals(AccessAuth.authFromReason(reason));
        persist(request, eventType, titleOf(eventType), reason, deny,
                AuditActors.displayName(CurrentUser.from(request)),
                AuditActors.operatorId(CurrentUser.from(request)),
                authSummary(request, null),
                resolvedAuthHttpStatus(request, deny, reason));
    }

    @Override
    public void login(
            HttpServletRequest request,
            boolean success,
            String operatorName,
            String message,
            String title) {
        login(request, new LoginAttempt(
                success, operatorName, null, null, null, message,
                hasText(title) ? title : (success ? "登录成功" : "登录失败"),
                success ? 200 : 401));
    }

    @Override
    public void login(HttpServletRequest request, LoginAttempt attempt) {
        if (attempt == null) {
            return;
        }
        if (request == null) {
            request = CurrentUser.currentRequest();
        }
        AccessAuth.stampLogin(request, attempt);
        persist(request, OperLogEventTypes.LOGIN, attempt.title(), attempt.message(), !attempt.success(),
                displayName(attempt), attempt.userId(),
                authSummary(request, attempt.grant()),
                attempt.httpStatus() > 0 ? attempt.httpStatus() : (attempt.success() ? 200 : 401));
    }

    private void persist(
            HttpServletRequest request,
            String eventType,
            String title,
            String message,
            boolean failed,
            String operatorName,
            Long operatorId,
            String requestSummary,
            int httpStatus) {
        OperLogRecorder recorder = recorder();
        if (recorder == null) {
            return;
        }
        try {
            recorder.record(new OperLogEntry(
                    title,
                    eventType,
                    request == null ? null : request.getMethod(),
                    request == null ? null : request.getRequestURI(),
                    requestSummary,
                    !failed,
                    trim(message, ERROR_MAX),
                    operatorId,
                    operatorName,
                    0,
                    httpStatus,
                    AuditActors.clientIp(request),
                    null,
                    MDC.get("traceId"),
                    LocalDateTime.now()));
        } catch (RuntimeException ignored) {
            // 审计失败不能打断业务
        }
    }

    private OperLogRecorder recorder() {
        if (recorder != null) {
            return recorder;
        }
        return recorders == null ? null : recorders.getIfAvailable();
    }

    static String persistEventType(String reason) {
        String auth = AccessAuth.authFromReason(reason);
        if (AccessAuth.UNCLASSIFIED.equals(auth)) {
            return OperLogEventTypes.AUTH_UNCLASSIFIED;
        }
        if (AccessAuth.DENY.equals(auth)) {
            return OperLogEventTypes.AUTH_DENY;
        }
        return null;
    }

    static String displayName(LoginAttempt attempt) {
        if (attempt.username() != null && attempt.username().contains("/")) {
            return attempt.username();
        }
        return AuditActors.format(attempt.username(), attempt.clientId());
    }

    static String authSummary(HttpServletRequest request, String grant) {
        StringBuilder sb = new StringBuilder();
        if (hasText(grant)) {
            sb.append("grant=").append(grant.trim());
        }
        String enforcement = AccessAuth.enforcementOf(request);
        if (hasText(enforcement)) {
            if (!sb.isEmpty()) {
                sb.append("; ");
            }
            sb.append("enforcement=").append(enforcement);
        }
        if (AccessAuth.jwtInvalid(request)) {
            if (!sb.isEmpty()) {
                sb.append("; ");
            }
            sb.append("jwt=invalid");
        }
        return sb.isEmpty() ? null : trim(sb.toString(), SUMMARY_MAX);
    }

    static int resolvedAuthHttpStatus(HttpServletRequest request, boolean deny, String reason) {
        Integer stamped = AccessAuth.httpStatusOf(request);
        if (stamped != null && stamped > 0) {
            return stamped;
        }
        if (!deny) {
            return 200;
        }
        String msg = reason == null ? "" : reason;
        if (msg.contains("unauthenticated")) {
            return 401;
        }
        return 403;
    }

    private static String titleOf(String eventType) {
        if (OperLogEventTypes.AUTH_UNCLASSIFIED.equals(eventType)) {
            return "未分类";
        }
        return "鉴权拒绝";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trim(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
