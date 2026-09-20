package cn.miniants.platform.ratelimit.spi;

import java.time.Instant;
import java.util.List;

/**
 * 当前后端全部（或截断后的）计数桶。
 */
public record RateLimitBucketSnapshot(
        Instant observedAt,
        String backend,
        boolean truncated,
        List<RateLimitBucketView> buckets) {

    public static RateLimitBucketSnapshot empty(String backend) {
        return new RateLimitBucketSnapshot(Instant.now(), backend, false, List.of());
    }
}
