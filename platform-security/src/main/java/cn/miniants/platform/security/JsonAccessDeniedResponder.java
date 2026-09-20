package cn.miniants.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * 鉴权拒绝写 Spring 错误体（{@code timestamp/status/error/message/path}）。
 * 给 Filter 和拦截器共用；不注册为默认 Bean，产品信封仍走 {@link AuthDeniedException}。
 */
public class JsonAccessDeniedResponder implements AccessDeniedResponder {

    private static final Logger log = LoggerFactory.getLogger(JsonAccessDeniedResponder.class);

    @Override
    public boolean deny(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String message) {
        try {
            write(request, response, status, message);
        } catch (IOException e) {
            log.warn("写入鉴权拒绝响应失败: {}", e.getMessage());
        }
        return false;
    }

    public static void write(HttpServletRequest request,
                             HttpServletResponse response,
                             HttpStatus status,
                             String message) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String body = "{\"timestamp\":\"" + Instant.now() + "\",\"status\":" + status.value()
                + ",\"error\":\"" + status.getReasonPhrase() + "\",\"message\":\"" + escapeJson(message)
                + "\",\"path\":\"" + escapeJson(errorPath(request)) + "\"}";
        response.getWriter().write(body);
    }

    private static String errorPath(HttpServletRequest request) {
        return request == null || request.getRequestURI() == null ? "" : request.getRequestURI();
    }

    private static String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
