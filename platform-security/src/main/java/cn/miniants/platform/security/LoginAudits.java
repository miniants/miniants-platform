package cn.miniants.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 登录 / 换票审计：标题、HTTP 状态、clientId 解析，以及落到 {@link SecurityAuditSink#login}。
 */
public final class LoginAudits {

    public static final String PASSWORD = "password";
    public static final String REFRESH = "refresh_token";
    public static final String EXTERNAL = "external";
    public static final String QR = "qr";
    public static final String CLIENT_CREDENTIALS = "client_credentials";
    public static final String CLIENT_SECRET = "client_secret";
    public static final String ID_NO = "id_no";

    private LoginAudits() {
    }

    public static String title(String grant, boolean success) {
        String g = grant == null ? "" : grant.trim();
        return switch (g) {
            case EXTERNAL -> success ? "外部身份登录成功" : "外部身份登录失败";
            case QR -> success ? "扫码登录成功" : "扫码登录失败";
            case REFRESH -> success ? "刷新令牌成功" : "刷新令牌失败";
            case CLIENT_CREDENTIALS -> success ? "客户端令牌成功" : "客户端令牌失败";
            case CLIENT_SECRET -> "客户端鉴权失败";
            case ID_NO -> success ? "身份证实名登录成功" : "身份证实名登录失败";
            default -> success ? "登录成功" : "登录失败";
        };
    }

    public static int httpStatus(String grant, boolean success, String message) {
        if (success) {
            return 200;
        }
        if (CLIENT_SECRET.equals(grant) || REFRESH.equals(grant) || CLIENT_CREDENTIALS.equals(grant)) {
            return 401;
        }
        String msg = message == null ? "" : message;
        if (msg.contains("滑块") || msg.contains("用户名或密码") || msg.contains("不完整")
                || msg.contains("已失效") || msg.contains("为空") || msg.contains("登录门面")) {
            return 400;
        }
        return 401;
    }

    public static void record(SecurityAuditSink sink, HttpServletRequest request, LoginAttempt attempt) {
        if (sink == null || attempt == null) {
            return;
        }
        sink.login(request, attempt);
    }

    public static String clientIdOf(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String form = request.getParameter("client_id");
        if (form != null && !form.isBlank()) {
            return form.trim();
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.length() < 6 || !header.regionMatches(true, 0, "Basic ", 0, 6)) {
            return null;
        }
        try {
            String decoded = new String(
                    Base64.getDecoder().decode(header.substring(6).trim()), StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            String id = colon < 0 ? decoded : decoded.substring(0, colon);
            return id.isBlank() ? null : id.trim();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static Long userIdOf(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public static String textOf(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        return text.isEmpty() || "null".equals(text) ? null : text;
    }
}
