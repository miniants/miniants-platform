package cn.miniants.platform.ratelimit.support;

import cn.miniants.platform.ratelimit.RateLimitDecision;
import cn.miniants.platform.ratelimit.RateLimitExceededException;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 标准限流响应头。
 */
public final class RateLimitHttpHeaders {

    public static final String RETRY_AFTER = "Retry-After";
    public static final String RATE_LIMIT_LIMIT = "RateLimit-Limit";
    public static final String RATE_LIMIT_REMAINING = "RateLimit-Remaining";
    public static final String RATE_LIMIT_RESET = "RateLimit-Reset";

    /** 访问日志可读原因属性（与 observability 对齐时可选用）。 */
    public static final String ACCESS_ERR_ATTR = "platform.access.err";
    /** 写入 {@code pl.access} 的 reason，与 observability {@code AccessLog.ATTR_REASON} 对齐。 */
    public static final String ACCESS_REASON_ATTR = "platform.access.reason";

    private RateLimitHttpHeaders() {
    }

    public static Map<String, String> from(RateLimitDecision decision) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (decision == null) {
            return headers;
        }
        long retrySeconds = Math.max(0L, decision.retryAfter() == null
                ? 0L
                : decision.retryAfter().getSeconds());
        if (retrySeconds == 0L && decision.retryAfter() != null && !decision.retryAfter().isZero()) {
            retrySeconds = 1L;
        }
        headers.put(RETRY_AFTER, Long.toString(retrySeconds));
        headers.put(RATE_LIMIT_LIMIT, Long.toString(decision.limit()));
        headers.put(RATE_LIMIT_REMAINING, Long.toString(Math.max(0L, decision.remaining())));
        Instant resetAt = decision.resetAt();
        if (resetAt == null) {
            resetAt = Instant.now().plus(decision.retryAfter() == null ? Duration.ZERO : decision.retryAfter());
        }
        headers.put(RATE_LIMIT_RESET, Long.toString(resetAt.getEpochSecond()));
        return headers;
    }

    public static Map<String, String> from(RateLimitExceededException ex) {
        return from(ex == null ? null : ex.decision());
    }
}
