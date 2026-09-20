package cn.miniants.platform.ratelimit.metrics;

import cn.miniants.platform.ratelimit.RateLimitAlgorithm;
import cn.miniants.platform.ratelimit.RateLimitBackend;
import cn.miniants.platform.ratelimit.RateLimitDecision;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MicrometerRateLimitMetricsTest {

    @Test
    void recordsOnlyAllowedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerRateLimitMetrics metrics = new MicrometerRateLimitMetrics(registry);
        RateLimitDecision denied = RateLimitDecision.denied(
                10, 0, Duration.ofSeconds(1), Instant.now(),
                "auth.login", RateLimitAlgorithm.GCRA, RateLimitBackend.REDIS);
        metrics.record(denied, Duration.ofMillis(3));
        metrics.recordPublishError("redis");

        Set<String> tagKeys = registry.getMeters().stream()
                .map(Meter::getId)
                .flatMap(id -> id.getTags().stream())
                .map(tag -> tag.getKey())
                .collect(Collectors.toSet());
        assertEquals(Set.of("policy", "algorithm", "backend", "outcome"), tagKeys);
        assertTrue(registry.find("platform.ratelimit.acquire").counter().count() > 0);
        assertTrue(registry.find("platform.ratelimit.publish.error").counter().count() > 0);
    }
}
