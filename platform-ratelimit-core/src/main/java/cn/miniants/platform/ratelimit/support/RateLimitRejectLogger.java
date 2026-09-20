package cn.miniants.platform.ratelimit.support;

import cn.miniants.platform.ratelimit.RateLimitDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * 拒绝日志：traceId、policy、明文主体、retryAfter。主体与 Redis 键同一套消毒。
 */
public final class RateLimitRejectLogger {

    private static final Logger log = LoggerFactory.getLogger(RateLimitRejectLogger.class);

    private RateLimitRejectLogger() {
    }

    public static void denied(RateLimitDecision decision, String subject) {
        if (decision == null || decision.allowed()) {
            return;
        }
        String traceId = MDC.get("traceId");
        log.warn("[pl] rate-limit deny policy={} subject={} outcome={} retryAfter={}ms traceId={}",
                decision.policyCode(),
                RateLimitSubjectKey.sanitize(subject),
                decision.outcome(),
                decision.retryAfter() == null ? 0L : decision.retryAfter().toMillis(),
                traceId == null || traceId.isBlank() ? "-" : traceId);
    }
}
