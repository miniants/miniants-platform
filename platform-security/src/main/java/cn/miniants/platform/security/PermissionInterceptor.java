package cn.miniants.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;
import java.util.function.BooleanSupplier;

public class PermissionInterceptor implements HandlerInterceptor {

    private final PermissionGuard permissionGuard = new PermissionGuard();
    private final EnforcementModeSource enforcementModeSource;
    private final SecurityAuditSink auditSink;
    private final PermissionPolicy permissionPolicy;
    private final AccessDeniedResponder deniedResponder;
    private final PublicAccessPaths publicAccessPaths;
    private final BooleanSupplier rejectUnclassified;

    public PermissionInterceptor(EnforcementModeSource enforcementModeSource, SecurityAuditSink auditSink) {
        this(enforcementModeSource, auditSink, new AnnotationPermissionPolicy(), null, null, () -> true);
    }

    public PermissionInterceptor(
            EnforcementModeSource enforcementModeSource,
            SecurityAuditSink auditSink,
            PermissionPolicy permissionPolicy,
            AccessDeniedResponder deniedResponder) {
        this(enforcementModeSource, auditSink, permissionPolicy, deniedResponder, null, () -> true);
    }

    public PermissionInterceptor(
            EnforcementModeSource enforcementModeSource,
            SecurityAuditSink auditSink,
            PermissionPolicy permissionPolicy,
            AccessDeniedResponder deniedResponder,
            PublicAccessPaths publicAccessPaths) {
        this(enforcementModeSource, auditSink, permissionPolicy, deniedResponder, publicAccessPaths, () -> true);
    }

    public PermissionInterceptor(
            EnforcementModeSource enforcementModeSource,
            SecurityAuditSink auditSink,
            PermissionPolicy permissionPolicy,
            AccessDeniedResponder deniedResponder,
            PublicAccessPaths publicAccessPaths,
            boolean rejectUnclassified) {
        this(enforcementModeSource, auditSink, permissionPolicy, deniedResponder, publicAccessPaths,
                () -> rejectUnclassified);
    }

    public PermissionInterceptor(
            EnforcementModeSource enforcementModeSource,
            SecurityAuditSink auditSink,
            PermissionPolicy permissionPolicy,
            AccessDeniedResponder deniedResponder,
            PublicAccessPaths publicAccessPaths,
            BooleanSupplier rejectUnclassified) {
        this.enforcementModeSource = enforcementModeSource == null
                ? () -> EnforcementMode.SHADOW
                : enforcementModeSource;
        this.auditSink = auditSink == null ? new RequestSecurityAuditSink() : auditSink;
        this.permissionPolicy = permissionPolicy == null ? new AnnotationPermissionPolicy() : permissionPolicy;
        this.deniedResponder = deniedResponder;
        this.publicAccessPaths = publicAccessPaths;
        this.rejectUnclassified = rejectUnclassified == null ? () -> true : rejectUnclassified;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if (isErrorDispatch(request)) {
            return true;
        }
        EnforcementMode mode = enforcementModeSource.current();
        if (mode == null) {
            mode = EnforcementMode.SHADOW;
        }
        AccessAuth.stampEnforcement(request, mode);
        Actor actor = resolveActor(request);
        if (mode == EnforcementMode.OFF) {
            AccessAuth.stampHttpStatus(request, 200);
            auditSink.record(request, "enforcement-off", actor);
            return true;
        }
        if (AccessAuth.alreadyPermitted(request)) {
            return true;
        }
        if (hasPublicAccess(handlerMethod) || protocolPublic(request)) {
            auditSink.record(request, AccessAuth.publicOkReason(actor), actor);
            return true;
        }
        if (OwnedInterceptor.hasOwnedResolver(handlerMethod)) {
            return true; // 本人档（@Authenticated(resolver=…)）由 OwnedInterceptor 把关
        }
        PermissionPolicy.Requirement requirement = permissionPolicy.resolve(handlerMethod, request.getMethod());
        String required = requirement == null || requirement.ignore() ? null : requirement.code();
        boolean authenticatedOnly = hasAuthenticated(handlerMethod);
        if (permissionPolicy.skip(handlerMethod, request, actor)) {
            return true;
        }
        if (actor == Actor.CLIENT) {
            if (required == null && authenticatedOnly) {
                auditSink.record(request, "authenticated-ok", actor);
                return true;
            }
            return handleClient(request, response, requirement, mode, actor);
        }
        if (actor == Actor.ANON) {
            return rejectUnauthenticated(request, response, actor);
        }
        if (requirement == null) {
            if (authenticatedOnly) {
                auditSink.record(request, "authenticated-ok", actor);
                return true;
            }
            if (permissionPolicy.allowUnclassified(handlerMethod, actor)) {
                return true;
            }
            boolean reject = mode == EnforcementMode.ENFORCE && rejectUnclassified.getAsBoolean();
            AccessAuth.stampHttpStatus(request, reject ? HttpStatus.FORBIDDEN.value() : HttpStatus.OK.value());
            auditSink.record(request, "permission-unclassified", actor);
            if (reject) {
                return deny(request, response, HttpStatus.FORBIDDEN, "权限不足");
            }
            return true;
        }
        CurrentUser user = CurrentUser.from(request);
        boolean admin = user != null && user.sysAdmin();
        PermissionGuard.Outcome outcome = permissionGuard.decide(
                requirement.ignore(), requirement.code(), admin,
                user == null ? Set.of() : user.permissions(), mode);
        return switch (outcome) {
            case ALLOW -> {
                String matched = admin ? "admin"
                        : PermissionGuard.firstMatch(user == null ? Set.of() : user.permissions(), requirement.code());
                String reason = requirement.ignore() ? "permission-unclassified"
                        : "permission-ok:" + (matched == null ? requirement.code() : matched);
                auditSink.record(request, reason, actor);
                yield true;
            }
            case SHADOW_DENY -> {
                AccessAuth.stampHttpStatus(request, HttpStatus.OK.value());
                auditSink.record(request, "permission-mismatch:" + requirement.code(), actor);
                yield true;
            }
            case DENY -> {
                AccessAuth.stampHttpStatus(request, HttpStatus.FORBIDDEN.value());
                auditSink.record(request, "permission-mismatch:" + requirement.code(), actor);
                yield deny(request, response, HttpStatus.FORBIDDEN, "权限不足");
            }
        };
    }

    private boolean handleClient(
            HttpServletRequest request,
            HttpServletResponse response,
            PermissionPolicy.Requirement requirement,
            EnforcementMode mode,
            Actor actor) {
        CurrentUser user = CurrentUser.from(request);
        Set<String> granted = user == null ? Set.of() : user.scopes();
        String[] required = permissionPolicy.requiredScopes(requirement);
        if (required.length == 0) {
            AccessAuth.stampHttpStatus(request, HttpStatus.OK.value());
            auditSink.record(request, "scope-unclassified", actor);
            return true;
        }
        String matched = permissionPolicy.firstMatchingScope(granted, required);
        if (matched != null) {
            auditSink.record(request, "scope-ok:" + matched, actor);
            return true;
        }
        String reason = "scope-mismatch:" + String.join("|", required);
        boolean reject = mode == EnforcementMode.ENFORCE;
        AccessAuth.stampHttpStatus(request, reject ? HttpStatus.FORBIDDEN.value() : HttpStatus.OK.value());
        auditSink.record(request, reason, actor);
        if (reject) {
            return deny(request, response, HttpStatus.FORBIDDEN, "权限不足");
        }
        return true;
    }

    private boolean rejectUnauthenticated(
            HttpServletRequest request,
            HttpServletResponse response,
            Actor actor) {
        AccessAuth.stampHttpStatus(request, HttpStatus.UNAUTHORIZED.value());
        String reason = AccessAuth.jwtInvalid(request)
                ? "permission-unauthenticated:bad-token"
                : "permission-unauthenticated";
        auditSink.record(request, reason, actor);
        return deny(request, response, HttpStatus.UNAUTHORIZED, "未鉴权认证");
    }

    private boolean deny(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String message) {
        if (deniedResponder != null) {
            return deniedResponder.deny(request, response, status, message);
        }
        throw new AuthDeniedException(
                status == HttpStatus.UNAUTHORIZED ? SecurityCodes.UNAUTHENTICATED : SecurityCodes.FORBIDDEN,
                status);
    }

    private static Actor resolveActor(HttpServletRequest request) {
        CurrentUser user = CurrentUser.from(request);
        return user == null ? Actor.ANON : user.actor();
    }

    private boolean protocolPublic(HttpServletRequest request) {
        return publicAccessPaths != null && publicAccessPaths.matches(request);
    }

    private static boolean hasPublicAccess(HandlerMethod handlerMethod) {
        return PublicAccessPathRegistrar.annotated(handlerMethod);
    }

    private static boolean hasAuthenticated(HandlerMethod handlerMethod) {
        return handlerMethod.getMethodAnnotation(Authenticated.class) != null
                || handlerMethod.getBeanType().getAnnotation(Authenticated.class) != null;
    }

    static boolean isErrorDispatch(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return "/error".equals(uri) || (uri != null && uri.endsWith("/error"));
    }
}
