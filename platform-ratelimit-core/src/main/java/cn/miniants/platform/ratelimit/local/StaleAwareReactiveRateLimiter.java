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
import cn.miniants.platform.ratelimit.spi.ReactiveRateLimiter;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

public class StaleAwareReactiveRateLimiter implements ReactiveRateLimiter {

    private final ReactiveRateLimiter delegate;
    private final RateLimitPolicyRegistry registry;
    private final RateLimitProperties properties;
    private final RateLimitMetricsRecorder metrics;
    private final RateLimitBackend backend;

    public StaleAwareReactiveRateLimiter(
            ReactiveRateLimiter delegate,
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

    @Override
    public Mono<RateLimitDecision> acquire(RateLimitRequest request, RateLimitPolicy policy) {
        RateLimitDecision stale = staleDecision(policy);
        if (stale != null) {
            metrics.record(stale, Duration.ZERO);
            return Mono.just(stale);
        }
        long start = System.nanoTime();
        return delegate.acquire(request, policy)
                .doOnNext(decision -> metrics.record(decision, Duration.ofNanos(System.nanoTime() - start)))
                .doOnError(ignored -> metrics.recordStoreError(
                        policy.policyCode(),
                        policy.algorithm().name(),
                        backend.name()));
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
