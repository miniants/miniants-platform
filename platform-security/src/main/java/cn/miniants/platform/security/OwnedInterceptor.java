package cn.miniants.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * 本人数据归属拦截器：只处理 {@code @Authenticated(resolver != None)} 的方法。
 * 仅登录档（resolver 缺省 {@link OwnedResolver.None}）仍由 {@link PermissionInterceptor} 把关；
 * {@link PermissionInterceptor} 对 resolver 模式方法内置跳过，避免双重判定。
 */
public class OwnedInterceptor implements HandlerInterceptor {

    private final EnforcementModeSource enforcementModeSource;
    private final SecurityAuditSink auditSink;
    private final AccessDeniedResponder deniedResponder;
    private final BeanFactory beanFactory;
    private final boolean adminBypass;

    public OwnedInterceptor(EnforcementModeSource enforcementModeSource,
                            SecurityAuditSink auditSink,
                            AccessDeniedResponder deniedResponder,
                            BeanFactory beanFactory,
                            boolean adminBypass) {
        this.enforcementModeSource = enforcementModeSource == null
                ? () -> EnforcementMode.SHADOW
                : enforcementModeSource;
        this.auditSink = auditSink == null ? new RequestSecurityAuditSink() : auditSink;
        this.deniedResponder = deniedResponder;
        this.beanFactory = beanFactory;
        this.adminBypass = adminBypass;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if (PermissionInterceptor.isErrorDispatch(request)) {
            return true;
        }
        Authenticated owned = ownedAnnotation(handlerMethod);
        if (owned == null || owned.resolver() == OwnedResolver.None.class) {
            return true;
        }
        if (PublicAccessPathRegistrar.annotated(handlerMethod)) {
            return true; // 公开优先
        }
        EnforcementMode mode = enforcementModeSource.current();
        if (mode == null) {
            mode = EnforcementMode.SHADOW;
        }
        AccessAuth.stampEnforcement(request, mode);
        if (mode == EnforcementMode.OFF || AccessAuth.alreadyPermitted(request)) {
            return true;
        }
        Actor actor = actorOf(request);
        if (actor == Actor.ANON) {
            AccessAuth.stampHttpStatus(request, HttpStatus.UNAUTHORIZED.value());
            String reason = AccessAuth.jwtInvalid(request)
                    ? "owned-unauthenticated:bad-token"
                    : "owned-unauthenticated";
            auditSink.record(request, reason, actor);
            return deny(request, response, HttpStatus.UNAUTHORIZED, "未登录");
        }
        if (actor == Actor.CLIENT) {
            boolean reject = mode == EnforcementMode.ENFORCE;
            AccessAuth.stampHttpStatus(request, reject ? HttpStatus.FORBIDDEN.value() : HttpStatus.OK.value());
            auditSink.record(request, "owned-not-user", actor);
            return reject
                    ? deny(request, response, HttpStatus.FORBIDDEN, "只能操作本人数据")
                    : true;
        }
        CurrentUser user = CurrentUser.from(request);
        if (user != null && user.sysAdmin() && adminBypass) {
            OwnedContext.bind(request, new OwnedContext(user, null));
            auditSink.record(request, "owned-ok:admin", actor);
            return true;
        }
        OwnedResolver resolver = beanFactory.getBean(owned.resolver());
        String resourceKey = readParam(request, owned.param());
        if (StringUtils.hasText(owned.param()) && !StringUtils.hasText(resourceKey)) {
            boolean reject = mode == EnforcementMode.ENFORCE;
            AccessAuth.stampHttpStatus(request, reject ? HttpStatus.UNAUTHORIZED.value() : HttpStatus.OK.value());
            auditSink.record(request, "owned-unresolved", actor);
            return reject
                    ? deny(request, response, HttpStatus.UNAUTHORIZED, "无法确认本人身份")
                    : true;
        }
        OwnedResolver.Resolution result =
                resolver.resolve(new OwnedResolver.OwnedRequest(resourceKey, user, request));
        if (result instanceof OwnedResolver.Resolution.Ok) {
            OwnedContext.bind(request, new OwnedContext(user, ((OwnedResolver.Resolution.Ok) result).subject()));
            String suffix = resolver.okSuffix();
            auditSink.record(request,
                    StringUtils.hasText(suffix) ? "owned-ok:" + suffix : "owned-ok", actor);
            return true;
        }
        if (result instanceof OwnedResolver.Resolution.Unresolved) {
            boolean reject = mode == EnforcementMode.ENFORCE;
            AccessAuth.stampHttpStatus(request, reject ? HttpStatus.UNAUTHORIZED.value() : HttpStatus.OK.value());
            auditSink.record(request, "owned-unresolved", actor);
            return reject
                    ? deny(request, response, HttpStatus.UNAUTHORIZED, "无法确认本人身份")
                    : true;
        }
        boolean reject = mode == EnforcementMode.ENFORCE;
        AccessAuth.stampHttpStatus(request, reject ? HttpStatus.FORBIDDEN.value() : HttpStatus.OK.value());
        auditSink.record(request, "owned-not-owner", actor);
        return reject
                ? deny(request, response, HttpStatus.FORBIDDEN, "只能操作本人数据")
                : true;
    }

    private boolean deny(HttpServletRequest request, HttpServletResponse response,
                         HttpStatus status, String message) {
        if (deniedResponder != null) {
            return deniedResponder.deny(request, response, status, message);
        }
        throw new AuthDeniedException(
                status == HttpStatus.UNAUTHORIZED ? SecurityCodes.UNAUTHENTICATED : SecurityCodes.FORBIDDEN,
                status,
                message);
    }

    /** 方法注解优先，其次类注解（与 {@link PermissionInterceptor#hasAuthenticated} 同序）。 */
    static Authenticated ownedAnnotation(HandlerMethod handlerMethod) {
        Authenticated method = handlerMethod.getMethodAnnotation(Authenticated.class);
        return method != null ? method : handlerMethod.getBeanType().getAnnotation(Authenticated.class);
    }

    static boolean hasOwnedResolver(HandlerMethod handlerMethod) {
        Authenticated owned = ownedAnnotation(handlerMethod);
        return owned != null && owned.resolver() != OwnedResolver.None.class;
    }

    private static Actor actorOf(HttpServletRequest request) {
        CurrentUser user = CurrentUser.from(request);
        return user == null ? Actor.ANON : user.actor();
    }

    @SuppressWarnings("unchecked")
    private static String readParam(HttpServletRequest request, String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        String query = request.getParameter(name);
        if (StringUtils.hasText(query)) {
            return query;
        }
        Object raw = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (raw instanceof Map<?, ?> vars) {
            Object value = vars.get(name);
            return value == null ? null : String.valueOf(value);
        }
        return null;
    }
}
