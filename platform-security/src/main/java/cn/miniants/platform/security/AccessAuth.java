package cn.miniants.platform.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 访问日志鉴权字段。属性名 {@code platform.access.*}，与 {@code pl.access} 正文键对齐。
 */
public final class AccessAuth {

    public static final String AUTH = "platform.access.auth";
    public static final String REASON = "platform.access.reason";
    public static final String ACTOR = "platform.access.actor";
    public static final String LOGIN = "platform.access.login";
    public static final String OP = "platform.access.op";
    public static final String ENFORCEMENT = "platform.access.enforcement";
    public static final String HTTP_STATUS = "platform.access.httpStatus";
    public static final String JWT = "platform.access.jwt";
    public static final String JWT_INVALID = "invalid";

    public static final String NONE = "none";
    public static final String UNCLASSIFIED = "unclassified";
    public static final String DENY = "deny";
    public static final String OK = "ok";
    public static final String PERMIT = "permit";

    private AccessAuth() {
    }

    public static void stamp(HttpServletRequest request, String auth, String reason, Actor actor) {
        if (request == null) {
            return;
        }
        if (auth != null && !auth.isBlank()) {
            request.setAttribute(AUTH, auth);
        }
        if (reason != null && !reason.isBlank()) {
            request.setAttribute(REASON, reason);
        }
        if (actor != null) {
            request.setAttribute(ACTOR, actor.wire());
        }
    }

    public static String authOf(HttpServletRequest request) {
        return attr(request, AUTH);
    }

    public static String reasonOf(HttpServletRequest request) {
        return attr(request, REASON);
    }

    public static String actorOf(HttpServletRequest request) {
        return attr(request, ACTOR);
    }

    public static boolean alreadyPermitted(HttpServletRequest request) {
        return PERMIT.equals(authOf(request));
    }

    public static void stampLogin(HttpServletRequest request, boolean success) {
        stampLogin(request, success ? "ok" : "fail");
    }

    public static void stampLogin(HttpServletRequest request, LoginAttempt attempt) {
        if (attempt == null) {
            return;
        }
        if (attempt.success()) {
            stampLogin(request, "ok");
            return;
        }
        stampLogin(request, UnboundAccount.matches(attempt.message()) ? "unbound" : "fail");
    }

    public static void stampLogin(HttpServletRequest request, String outcome) {
        if (request == null || outcome == null || outcome.isBlank()) {
            return;
        }
        request.setAttribute(LOGIN, outcome);
    }

    public static String loginOf(HttpServletRequest request) {
        return attr(request, LOGIN);
    }

    public static void stampEnforcement(HttpServletRequest request, EnforcementMode mode) {
        if (request == null || mode == null) {
            return;
        }
        request.setAttribute(ENFORCEMENT, mode.wire());
    }

    public static String enforcementOf(HttpServletRequest request) {
        return attr(request, ENFORCEMENT);
    }

    public static void stampHttpStatus(HttpServletRequest request, int status) {
        if (request == null) {
            return;
        }
        request.setAttribute(HTTP_STATUS, status);
    }

    public static Integer httpStatusOf(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object value = request.getAttribute(HTTP_STATUS);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public static void stampJwtInvalid(HttpServletRequest request) {
        if (request == null) {
            return;
        }
        request.setAttribute(JWT, JWT_INVALID);
    }

    public static boolean jwtInvalid(HttpServletRequest request) {
        return JWT_INVALID.equals(attr(request, JWT));
    }

    public static void stampOp(HttpServletRequest request, String op) {
        if (request == null || op == null || op.isBlank()) {
            return;
        }
        request.setAttribute(OP, op.trim());
    }

    public static String opOf(HttpServletRequest request) {
        return attr(request, OP);
    }

    public static String publicOkReason(Actor actor) {
        if (actor == Actor.USER) {
            return "public-ok:user";
        }
        if (actor == Actor.CLIENT) {
            return "public-ok:client";
        }
        return "public-ok:anon";
    }

    public static String authFromReason(String reason) {
        String msg = reason == null ? "" : reason;
        if (msg.contains("unclassified")) {
            return UNCLASSIFIED;
        }
        if (msg.startsWith("public-ok") || msg.contains("permit")) {
            return PERMIT;
        }
        if (msg.contains("mismatch") || msg.contains("unauthenticated")
                || msg.contains("not-owner") || msg.contains("not-user")
                || msg.contains("owned-unresolved")) {
            return DENY;
        }
        if (msg.contains("-ok")) {
            return OK;
        }
        if (msg.contains("enforcement-off")) {
            return NONE;
        }
        return NONE;
    }

    private static String attr(HttpServletRequest request, String name) {
        if (request == null) {
            return null;
        }
        Object value = request.getAttribute(name);
        return value == null ? null : String.valueOf(value);
    }
}
