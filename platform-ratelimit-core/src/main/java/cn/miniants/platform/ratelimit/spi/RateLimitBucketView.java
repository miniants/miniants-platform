package cn.miniants.platform.ratelimit.spi;

/**
 * 一只计数桶的只读快照：策略 × 明文主体。
 */
public record RateLimitBucketView(
        String policyCode,
        String subject,
        String algorithm,
        long limit,
        long remaining,
        long retryAfterMs,
        long resetAtMs) {
}
