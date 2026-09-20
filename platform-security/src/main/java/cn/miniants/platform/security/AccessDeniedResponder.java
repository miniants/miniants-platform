package cn.miniants.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;

/**
 * 应用鉴权失败响应适配点。返回 {@code false} 表示响应已写完并终止 MVC 链。
 */
@FunctionalInterface
public interface AccessDeniedResponder {

    boolean deny(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String message);
}
