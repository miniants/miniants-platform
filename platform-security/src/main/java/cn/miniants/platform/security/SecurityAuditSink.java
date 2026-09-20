package cn.miniants.platform.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 安全审计：拦截器/门闸用 {@link #record}；登录/换票用 {@link #login}。
 * 默认只盖访问字段；有库的进程由 admin 落 {@code sys_oper_log}。
 */
public interface SecurityAuditSink {

    void record(HttpServletRequest request, String reason, Actor actor);

    default void login(boolean success, String operatorName, String message, String title) {
        login(CurrentUser.currentRequest(), success, operatorName, message, title);
    }

    default void login(boolean success, String operatorName, String message) {
        login(success, operatorName, message, null);
    }

    default void login(
            HttpServletRequest request,
            boolean success,
            String operatorName,
            String message,
            String title) {
        login(request, new LoginAttempt(
                success, operatorName, null, null, null, message, title, success ? 200 : 401));
    }

    default void login(HttpServletRequest request, LoginAttempt attempt) {
    }
}
