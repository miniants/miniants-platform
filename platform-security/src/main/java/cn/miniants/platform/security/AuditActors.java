package cn.miniants.platform.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 审计操作者：账号优先；展示为 {@code 用户名/clientId}。无用户有 client 时为 {@code -/clientId}。
 */
public final class AuditActors {

    public static final int DISPLAY_MAX = 64;

    private AuditActors() {
    }

    public static String operatorName(CurrentUser user) {
        if (user == null) {
            return null;
        }
        if (hasText(user.username())) {
            return user.username();
        }
        if (hasText(user.name())) {
            return user.name();
        }
        return blankToNull(user.clientId());
    }

    public static Long operatorId(CurrentUser user) {
        return user == null ? null : user.userId();
    }

    public static String displayName(CurrentUser user) {
        if (user == null) {
            return format(null, null);
        }
        return format(operatorName(user), user.clientId());
    }

    public static String displayName(HttpServletRequest request) {
        return displayName(CurrentUser.from(request));
    }

    /**
     * {@code 用户名/clientId}；无用户有 client 为 {@code -/clientId}；都无则空。
     * 原文保留，落库再按列宽截断。
     */
    public static String format(String username, String clientId) {
        boolean hasUser = hasText(username);
        boolean hasClient = hasText(clientId);
        if (hasClient) {
            String left = hasUser ? username.trim() : "-";
            return left + "/" + clientId.trim();
        }
        return hasUser ? username.trim() : null;
    }

    public static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (hasText(xff)) {
            return clip(xff.split(",")[0].trim(), DISPLAY_MAX);
        }
        String realIp = request.getHeader("X-Real-IP");
        if (hasText(realIp)) {
            return clip(realIp.trim(), DISPLAY_MAX);
        }
        return clip(request.getRemoteAddr(), DISPLAY_MAX);
    }

    static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return hasText(value) ? value : null;
    }
}
