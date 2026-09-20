package cn.miniants.platform.ratelimit.local;

import cn.miniants.platform.ratelimit.RateLimitBackend;
import cn.miniants.platform.ratelimit.RateLimitDecision;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.RateLimitRequest;
import cn.miniants.platform.ratelimit.StoreFailurePolicy;
import cn.miniants.platform.ratelimit.config.RateLimitProperties;
import cn.miniants.platform.ratelimit.metrics.RateLimitMetricsRecorder;
import cn.miniants.platform.ratelimit.policy.CompositeRateLimitPolicyRegistry;
import cn.miniants.platform.ratelimit.spi.RateLimitPolicyRegistry;
import cn.miniants.platform.ratelimit.spi.RateLimiter;

import java.time.Duration;
import java.util.Objects;

/**
 * 动态策略快照超过最大陈旧时限后，按该策略的 ALLOW/DENY 故障语义执行，不再使用过期额度。
 */
public class StaleAwareRateLimiter implements RateLimiter {

    private final RateLimiter delegate;
    private final RateLimitPolicyRegistry registry;
    private final RateLimitProperties properties;
    private final RateLimitMetricsRecorder metrics;
    private final RateLimitBackend backend;

    public StaleAwareRateLimiter(
            RateLimiter delegate,
            RateLimitPolicyRegistry registry,
            RateLimitProperties properties,
            RateLimitMetricsRecorder metrics,
            RateLimitBackend backend) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.metrics = metrics == null ? RateLimitMetricsRecorder.NOOP : metrics;
        this.backend = backend == null ? RateLimitBackend.LOCAL : backend;
    }

    public RateLimiter delegate() {
        return delegate;
    }

    @Override
    public RateLimitDecision acquire(RateLimitRequest request, RateLimitPolicy policy) {
        RateLimitDecision stale = staleDecision(policy);
        if (stale != null) {
            metrics.record(stale, Duration.ZERO);
            return stale;
        }
        long start = System.nanoTime();
        try {
            RateLimitDecision decision = delegate.acquire(request, policy);
            metrics.record(decision, Duration.ofNanos(System.nanoTime() - start));
            return decision;
        } catch (RuntimeException ex) {
            metrics.recordStoreError(
                    policy.policyCode(),
                    policy.algorithm().name(),
                    backend.name());
            throw ex;
        }
    }

    private RateLimitDecision staleDecision(RateLimitPolicy policy) {
        if (!(registry instanceof CompositeRateLimitPolicyRegistry composite)) {
            return null;
        }
        if (!composite.shouldApplyStaleFailure(policy.policyCode(), properties.getStaleSnapshotMaxAge())) {
            return null;
        }
        if (policy.storeFailurePolicy() == StoreFailurePolicy.ALLOW) {
            return RateLimitDecision.staleAllow(
                    policy.limit(), policy.policyCode(), policy.algorithm(), backend);
        }
        return RateLimitDecision.staleDeny(
                policy.limit(), policy.policyCode(), policy.algorithm(), backend);
    }
}
