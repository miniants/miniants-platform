package cn.miniants.platform.ratelimit.local;

import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.policy.CompositeRateLimitPolicyRegistry;
import cn.miniants.platform.ratelimit.spi.RateLimitBucketInspector;
import cn.miniants.platform.ratelimit.spi.RateLimitBucketSnapshot;
import cn.miniants.platform.ratelimit.spi.RateLimitBucketView;
import cn.miniants.platform.ratelimit.spi.RateLimitClock;
import cn.miniants.platform.ratelimit.spi.RateLimitPolicyRegistry;
import cn.miniants.platform.ratelimit.spi.RateLimiter;
import cn.miniants.platform.ratelimit.spi.SystemRateLimitClock;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 从本地 GCRA / 滑动窗口内存表列出全部桶。
 */
public class LocalRateLimitBucketInspector implements RateLimitBucketInspector {

    private final RateLimiter limiter;
    private final RateLimitPolicyRegistry registry;
    private final RateLimitClock clock;

    public LocalRateLimitBucketInspector(RateLimiter limiter, RateLimitPolicyRegistry registry) {
        this(limiter, registry, SystemRateLimitClock.INSTANCE);
    }

    public LocalRateLimitBucketInspector(
            RateLimiter limiter, RateLimitPolicyRegistry registry, RateLimitClock clock) {
        this.limiter = Objects.requireNonNull(limiter, "limiter");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.clock = clock == null ? SystemRateLimitClock.INSTANCE : clock;
    }

    @Override
    public RateLimitBucketSnapshot inspect(int maxBuckets) {
        int max = Math.max(1, Math.min(maxBuckets, 2000));
        CompositeLocalRateLimiter local = unwrap(limiter);
        if (local == null) {
            return RateLimitBucketSnapshot.empty("local");
        }
        long nowMs = clock.currentTimeMillis();
        List<RateLimitBucketView> rows = local.inspect(collectPolicies(registry), nowMs, max);
        boolean truncated = local.size() > rows.size();
        return new RateLimitBucketSnapshot(Instant.ofEpochMilli(nowMs), "local", truncated, List.copyOf(rows));
    }

    static Map<String, RateLimitPolicy> collectPolicies(RateLimitPolicyRegistry registry) {
        Map<String, RateLimitPolicy> policies = new LinkedHashMap<>();
        if (registry instanceof CompositeRateLimitPolicyRegistry composite) {
            policies.putAll(composite.builtins());
            policies.putAll(composite.yamlBaseline());
            policies.putAll(composite.dynamicSnapshot());
        }
        return policies;
    }

    private static CompositeLocalRateLimiter unwrap(RateLimiter limiter) {
        RateLimiter current = limiter;
        if (current instanceof StaleAwareRateLimiter stale) {
            current = stale.delegate();
        }
        return current instanceof CompositeLocalRateLimiter composite ? composite : null;
    }
}
