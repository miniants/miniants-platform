package cn.miniants.platform.ratelimit.metrics;

import cn.miniants.platform.ratelimit.RateLimitDecision;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.Objects;

/**
 * Micrometer 打点。禁止把 subject / IP / 用户写入标签。
 */
public class MicrometerRateLimitMetrics implements RateLimitMetricsRecorder {

    private final MeterRegistry registry;

    public MicrometerRateLimitMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public void record(RateLimitDecision decision, Duration latency) {
        if (decision == null) {
            return;
        }
        Tags tags = tags(
                decision.policyCode(),
                decision.algorithm() == null ? "unknown" : decision.algorithm().name(),
                decision.backend() == null ? "unknown" : decision.backend().name(),
                decision.outcome() == null ? "unknown" : decision.outcome().name());
        registry.counter("platform.ratelimit.acquire", tags).increment();
        if (latency != null && !latency.isNegative()) {
            Timer.builder("platform.ratelimit.acquire.latency")
                    .tags(tags)
                    .register(registry)
                    .record(latency);
        }
        if (decision.outcome() != null && decision.outcome().name().startsWith("STORE_ERROR")) {
            registry.counter("platform.ratelimit.store.error", tags).increment();
        }
        if (decision.outcome() != null && decision.outcome().name().startsWith("STALE_POLICY")) {
            registry.counter("platform.ratelimit.stale.policy", tags).increment();
        }
    }

    @Override
    public void recordStoreError(String policyCode, String algorithm, String backend) {
        registry.counter("platform.ratelimit.store.error", tags(policyCode, algorithm, backend, "error"))
                .increment();
    }

    @Override
    public void recordPublishError(String backend) {
        registry.counter("platform.ratelimit.publish.error", tags("none", "none", backend, "error"))
                .increment();
    }

    private static Tags tags(String policy, String algorithm, String backend, String outcome) {
        return Tags.of(
                "policy", blankToUnknown(policy),
                "algorithm", blankToUnknown(algorithm),
                "backend", blankToUnknown(backend),
                "outcome", blankToUnknown(outcome));
    }

    private static String blankToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
