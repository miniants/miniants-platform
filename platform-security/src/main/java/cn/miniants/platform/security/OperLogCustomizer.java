package cn.miniants.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;

/**
 * 操作日志应用适配点：可标记访问日志字段、过滤噪声并补充持久化所需字段。
 */
public interface OperLogCustomizer {

    default void discovered(HttpServletRequest request, HandlerMethod handlerMethod, OperLog operLog) {
    }

    default boolean shouldRecord(HttpServletRequest request, HandlerMethod handlerMethod, OperLog operLog) {
        return true;
    }

    default OperLogEntry customize(
            HttpServletRequest request,
            HttpServletResponse response,
            HandlerMethod handlerMethod,
            OperLog operLog,
            OperLogEntry entry) {
        return entry;
    }
}
