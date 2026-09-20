package cn.miniants.platform.observability;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 请求结束一行。渠道名 {@code pl.access}，不是 Java 包。
 * 鉴权字段读 {@code platform.access.*} request attribute（与 security {@code AccessAuth} 对齐），
 * 本模块不硬依赖 security。
 */
public final class AccessLog {

    public static final String LOGGER_NAME = "pl.access";

    static final String ATTR_AUTH = "platform.access.auth";
    static final String ATTR_REASON = "platform.access.reason";
    static final String ATTR_ACTOR = "platform.access.actor";
    static final String ATTR_LOGIN = "platform.access.login";
    static final String ATTR_OP = "platform.access.op";
    static final String ATTR_ERR = "platform.access.err";

    private static final Logger ACCESS = LoggerFactory.getLogger(LOGGER_NAME);

    private AccessLog() {
    }

    public static void emit(HttpServletRequest request, int status, long elapsedMs, Exception error) {
        String message = build(request, status, elapsedMs, error);
        Level level = levelFor(status, text(request, ATTR_LOGIN));
        if (level == Level.ERROR) {
            ACCESS.error("{}", message);
        } else if (level == Level.WARN) {
            ACCESS.warn("{}", message);
        } else {
            ACCESS.info("{}", message);
        }
    }

    /**
     * 2xx/3xx INFO；4xx 或换票 {@code login=fail} WARN；5xx ERROR。
     * 业务拒绝常仍是 HTTP 200；{@code login=unbound} 保持 INFO，只有 {@code login=fail} 升 WARN。
     */
    static Level levelFor(int status, String login) {
        if (status >= 500) {
            return Level.ERROR;
        }
        if (status >= 400 || "fail".equals(login)) {
            return Level.WARN;
        }
        return Level.INFO;
    }

    public static String build(HttpServletRequest request, int status, long elapsedMs, Exception error) {
        StringBuilder sb = new StringBuilder(160);
        sb.append("status=").append(status)
                .append(" ms=").append(elapsedMs)
                .append(" method=").append(dash(request.getMethod()))
                .append(" path=").append(dash(request.getRequestURI()));

        String auth = text(request, ATTR_AUTH);
        if (!StringUtils.hasText(auth)) {
            auth = "none";
        }
        sb.append(" auth=").append(auth);

        String reason = text(request, ATTR_REASON);
        if (StringUtils.hasText(reason)) {
            sb.append(" reason=").append(sanitize(reason));
        } else if ("none".equals(auth)) {
            sb.append(" reason=no-guard");
        }

        String actor = text(request, ATTR_ACTOR);
        if (StringUtils.hasText(actor)) {
            sb.append(" actor=").append(sanitize(actor));
        }

        String op = text(request, ATTR_OP);
        sb.append(" op=").append(StringUtils.hasText(op) ? sanitize(op) : "-");

        String login = text(request, ATTR_LOGIN);
        if (StringUtils.hasText(login)) {
            sb.append(" login=").append(sanitize(login));
        }

        if (error != null) {
            sb.append(" err=").append(error.getClass().getSimpleName());
        } else {
            String err = text(request, ATTR_ERR);
            if (StringUtils.hasText(err)) {
                sb.append(" err=").append(sanitize(err));
            }
        }
        return sb.toString();
    }

    public static boolean shouldSkip(HttpServletRequest request) {
        return isNoisePath(request == null ? null : request.getRequestURI());
    }

    /**
     * actuator / favicon / 内核 runtime 口，以及客户端更新元数据探测
     *（{@code /release|updater/} + yml / yaml / blockmap）。安装包本身不算噪声。
     */
    public static boolean isNoisePath(String uri) {
        if (!StringUtils.hasText(uri)) {
            return false;
        }
        String path = uri.toLowerCase(Locale.ROOT);
        if (path.contains("/actuator/")
                || path.contains("/internal/runtime/")
                || path.endsWith("/internal/runtime")
                || path.endsWith("/favicon.ico")) {
            return true;
        }
        return isUpdaterMetadata(path);
    }

    static boolean isUpdaterMetadata(String path) {
        if (!path.contains("/release/") && !path.contains("/updater/")) {
            return false;
        }
        return path.endsWith(".yml") || path.endsWith(".yaml") || path.endsWith(".blockmap");
    }

    private static String text(HttpServletRequest request, String name) {
        if (request == null) {
            return null;
        }
        Object value = request.getAttribute(name);
        return value == null ? null : String.valueOf(value);
    }

    private static String dash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    private static String sanitize(String value) {
        return value.replaceAll("[\\s=]+", "_");
    }
}
