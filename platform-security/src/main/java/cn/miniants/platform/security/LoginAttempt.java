package cn.miniants.platform.security;

/**
 * 登录 / 换票审计入参。用户列由 {@link AuditActors#format(String, String)} 拼。
 */
public record LoginAttempt(
        boolean success,
        String username,
        String clientId,
        Long userId,
        String grant,
        String message,
        String title,
        int httpStatus
) {
    public static LoginAttempt of(
            boolean success,
            String username,
            String clientId,
            Long userId,
            String grant,
            String message) {
        return new LoginAttempt(
                success,
                username,
                clientId,
                userId,
                grant,
                message,
                LoginAudits.title(grant, success),
                LoginAudits.httpStatus(grant, success, message));
    }

    public static LoginAttempt of(
            boolean success,
            String username,
            String clientId,
            String grant,
            String message) {
        return of(success, username, clientId, null, grant, message);
    }

    public static LoginAttempt titled(
            boolean success,
            String username,
            String clientId,
            String grant,
            String message,
            String title) {
        return new LoginAttempt(
                success,
                username,
                clientId,
                null,
                grant,
                message,
                title == null || title.isBlank() ? LoginAudits.title(grant, success) : title,
                LoginAudits.httpStatus(grant, success, message));
    }
}
