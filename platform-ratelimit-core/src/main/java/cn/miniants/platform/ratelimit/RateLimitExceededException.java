package cn.miniants.platform.ratelimit;

import cn.miniants.platform.core.error.PlatformCodes;
import cn.miniants.platform.core.error.PlatformException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 限流拒绝异常。Web 适配层可读 {@link #decision()} 填 429 / Retry-After 等头。
 */
public class RateLimitExceededException extends PlatformException {

    private final RateLimitDecision decision;

    public RateLimitExceededException(RateLimitDecision decision) {
        super(PlatformCodes.TOO_MANY_REQUESTS, buildMessage(decision));
        this.decision = Objects.requireNonNull(decision, "decision");
    }

    public RateLimitExceededException(String message, RateLimitDecision decision) {
        super(PlatformCodes.TOO_MANY_REQUESTS, message);
        this.decision = Objects.requireNonNull(decision, "decision");
    }

    public RateLimitDecision decision() {
        return decision;
    }

    public Duration retryAfter() {
        return decision.retryAfter();
    }

    public Instant resetAt() {
        return decision.resetAt();
    }

    public long remaining() {
        return decision.remaining();
    }

    public long limit() {
        return decision.limit();
    }

    public String policyCode() {
        return decision.policyCode();
    }

    private static String buildMessage(RateLimitDecision decision) {
        if (decision == null || decision.policyCode() == null || decision.policyCode().isBlank()) {
            return PlatformCodes.TOO_MANY_REQUESTS.defaultMessage();
        }
        return PlatformCodes.TOO_MANY_REQUESTS.defaultMessage() + "（策略 " + decision.policyCode() + "）";
    }
}
