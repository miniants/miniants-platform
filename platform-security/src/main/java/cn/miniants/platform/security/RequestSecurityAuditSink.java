package cn.miniants.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestSecurityAuditSink implements SecurityAuditSink {

    private static final Logger log = LoggerFactory.getLogger(RequestSecurityAuditSink.class);

    @Override
    public void record(HttpServletRequest request, String reason, Actor actor) {
        AccessAuth.stamp(request, AccessAuth.authFromReason(reason), reason, actor);
        if (reason != null && (reason.contains("unclassified") || reason.contains("mismatch")
                || reason.contains("unauthenticated"))) {
            log.info("[pl.security] {} {} -> {}",
                    request.getMethod(), request.getRequestURI(), reason);
        }
    }

    @Override
    public void login(
            HttpServletRequest request,
            boolean success,
            String operatorName,
            String message,
            String title) {
        login(request, new LoginAttempt(
                success, operatorName, null, null, null, message, title, success ? 200 : 401));
    }

    @Override
    public void login(HttpServletRequest request, LoginAttempt attempt) {
        if (attempt == null) {
            return;
        }
        AccessAuth.stampLogin(request, attempt);
        log.info("[pl.security] login success={} operator={} grant={} title={} msg={}",
                attempt.success(),
                displayName(attempt),
                attempt.grant(),
                attempt.title(),
                attempt.message());
    }

    private static String displayName(LoginAttempt attempt) {
        if (attempt.username() != null && attempt.username().contains("/")) {
            return attempt.username();
        }
        return AuditActors.format(attempt.username(), attempt.clientId());
    }
}
