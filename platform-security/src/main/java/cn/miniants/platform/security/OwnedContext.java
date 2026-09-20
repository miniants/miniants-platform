package cn.miniants.platform.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 当前请求上已由 {@link OwnedInterceptor} 校验过的主体。
 * 业务在本人档方法内用 {@link #find()} / {@link #require()} 读取解析结果。
 */
public final class OwnedContext {

    public static final String REQUEST_ATTR = "platform.security.owned.subject";

    private final CurrentUser user;
    private final Object subject;

    public OwnedContext(CurrentUser user, Object subject) {
        this.user = user;
        this.subject = subject;
    }

    public CurrentUser user() {
        return user;
    }

    /** 业务主体（项目解析器 {@link OwnedResolver.Resolution.Ok#subject()} 返回）；sysAdmin 放行时为 null。 */
    public Object subject() {
        return subject;
    }

    public static OwnedContext find() {
        HttpServletRequest request = CurrentUser.currentRequest();
        return request == null ? null : from(request);
    }

    public static OwnedContext from(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object raw = request.getAttribute(REQUEST_ATTR);
        return raw instanceof OwnedContext ctx ? ctx : null;
    }

    public static OwnedContext require() {
        OwnedContext ctx = find();
        if (ctx == null) {
            throw new IllegalStateException("当前接口未完成本人校验");
        }
        return ctx;
    }

    public static void bind(HttpServletRequest request, OwnedContext ctx) {
        if (request != null && ctx != null) {
            request.setAttribute(REQUEST_ATTR, ctx);
        }
    }

    public static void clear(HttpServletRequest request) {
        if (request != null) {
            request.removeAttribute(REQUEST_ATTR);
        }
    }
}
