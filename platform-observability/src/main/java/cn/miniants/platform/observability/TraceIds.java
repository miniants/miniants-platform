package cn.miniants.platform.observability;

import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 16 位 traceId：入站透传或新生成，与 uid、ip 一并写入 MDC。
 */
public final class TraceIds {

    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_UID = "uid";
    public static final String MDC_IP = "ip";
    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String ANON_UID = "-";
    public static final String ANON_IP = "-";

    private static final Pattern SAFE_TRACE = Pattern.compile("^[A-Za-z0-9_\\-]{8,64}$");

    private TraceIds() {
    }

    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static String resolve(String incoming) {
        if (StringUtils.hasText(incoming)) {
            String trimmed = incoming.trim();
            if (SAFE_TRACE.matcher(trimmed).matches()) {
                return trimmed.length() > 16 ? trimmed.substring(0, 16) : trimmed;
            }
        }
        return newTraceId();
    }

    public static Optional<String> current() {
        String value = MDC.get(MDC_TRACE_ID);
        return StringUtils.hasText(value) ? Optional.of(value) : Optional.empty();
    }

    public static void bind(String traceId) {
        if (StringUtils.hasText(traceId)) {
            MDC.put(MDC_TRACE_ID, traceId);
        } else {
            MDC.remove(MDC_TRACE_ID);
        }
    }

    public static void bindUid(String uid) {
        MDC.put(MDC_UID, StringUtils.hasText(uid) ? uid : ANON_UID);
    }

    public static void bindIp(String ip) {
        MDC.put(MDC_IP, StringUtils.hasText(ip) ? ip : ANON_IP);
    }

    public static void clear() {
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_UID);
        MDC.remove(MDC_IP);
    }
}
