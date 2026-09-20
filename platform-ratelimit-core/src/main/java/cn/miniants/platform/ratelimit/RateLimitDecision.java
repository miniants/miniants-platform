package cn.miniants.platform.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 限流决策结果，供调用方写响应头或抛拒绝异常。
 */
public record RateLimitDecision(
        boolean allowed,
        long limit,
        long remaining,
        Duration retryAfter,
        Instant resetAt,
        String policyCode,
        RateLimitAlgorithm algorithm,
        RateLimitBackend backend,
        RateLimitOutcome outcome
) {

    public RateLimitDecision {
        Objects.requireNonNull(outcome, "outcome");
        if (limit < 0) {
            throw new IllegalArgumentException("limit 不能为负");
        }
        if (remaining < 0) {
            remaining = 0;
        }
        if (retryAfter == null) {
            retryAfter = Duration.ZERO;
        }
        if (retryAfter.isNegative()) {
            retryAfter = Duration.ZERO;
        }
    }

    public static RateLimitDecision allowed(
            long limit,
            long remaining,
            Instant resetAt,
            String policyCode,
            RateLimitAlgorithm algorithm,
            RateLimitBackend backend) {
        return new RateLimitDecision(
                true,
                limit,
                remaining,
                Duration.ZERO,
                resetAt,
                policyCode,
                algorithm,
                backend,
                RateLimitOutcome.ALLOWED);
    }

    public static RateLimitDecision denied(
            long limit,
            long remaining,
            Duration retryAfter,
            Instant resetAt,
            String policyCode,
            RateLimitAlgorithm algorithm,
            RateLimitBackend backend) {
        return new RateLimitDecision(
                false,
                limit,
                remaining,
                retryAfter,
                resetAt,
                policyCode,
                algorithm,
                backend,
                RateLimitOutcome.DENIED);
    }

    public static RateLimitDecision storeErrorAllow(
            long limit,
            String policyCode,
            RateLimitAlgorithm algorithm,
            RateLimitBackend backend) {
        return new RateLimitDecision(
                true,
                limit,
                limit,
                Duration.ZERO,
                null,
                policyCode,
                algorithm,
                backend,
                RateLimitOutcome.STORE_ERROR_ALLOW);
    }

    public static RateLimitDecision storeErrorDeny(
            long limit,
            String policyCode,
            RateLimitAlgorithm algorithm,
            RateLimitBackend backend) {
        return new RateLimitDecision(
                false,
                limit,
                0,
                Duration.ZERO,
                null,
                policyCode,
                algorithm,
                backend,
                RateLimitOutcome.STORE_ERROR_DENY);
    }

    public static RateLimitDecision staleAllow(
            long limit,
            String policyCode,
            RateLimitAlgorithm algorithm,
            RateLimitBackend backend) {
        return new RateLimitDecision(
                true,
                limit,
                limit,
                Duration.ZERO,
                null,
                policyCode,
                algorithm,
                backend,
                RateLimitOutcome.STALE_POLICY_ALLOW);
    }

    public static RateLimitDecision staleDeny(
            long limit,
            String policyCode,
            RateLimitAlgorithm algorithm,
            RateLimitBackend backend) {
        return new RateLimitDecision(
                false,
                limit,
                0,
                Duration.ZERO,
                null,
                policyCode,
                algorithm,
                backend,
                RateLimitOutcome.STALE_POLICY_DENY);
    }
}
