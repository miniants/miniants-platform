package cn.miniants.platform.ratelimit.local;

import cn.miniants.platform.ratelimit.RateLimitAlgorithm;
import cn.miniants.platform.ratelimit.RateLimitBackend;
import cn.miniants.platform.ratelimit.RateLimitDecision;
import cn.miniants.platform.ratelimit.RateLimitOutcome;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.RateLimitRequest;
import cn.miniants.platform.ratelimit.StoreFailurePolicy;
import cn.miniants.platform.ratelimit.config.RateLimitProperties;
import cn.miniants.platform.ratelimit.metrics.RateLimitMetricsRecorder;
import cn.miniants.platform.ratelimit.policy.CompositeRateLimitPolicyRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaleAwareRateLimiterTest {

    @Test
    void staleDynamicPolicyUsesFailureSemantics() {
        RateLimitPolicy policy = RateLimitPolicy.builder()
                .policyCode("auth.login")
                .algorithm(RateLimitAlgorithm.GCRA)
                .limit(10)
                .period(Duration.ofSeconds(1))
                .storeFailurePolicy(StoreFailurePolicy.DENY)
                .build();
        CompositeRateLimitPolicyRegistry registry = new CompositeRateLimitPolicyRegistry(Map.of());
        registry.publishDynamicPolicies(Map.of("auth.login", policy), 1L);
        RateLimitProperties properties = new RateLimitProperties();
        properties.setStaleSnapshotMaxAge(Duration.ofMillis(1));
        sleep(5);
        StaleAwareRateLimiter limiter = new StaleAwareRateLimiter(
                (request, ignored) -> {
                    throw new AssertionError("过期快照不得继续扣额度");
                },
                registry,
                properties,
                RateLimitMetricsRecorder.NOOP,
                RateLimitBackend.LOCAL);
        RateLimitDecision decision = limiter.acquire(RateLimitRequest.of("auth.login", "ip"), policy);
        assertEquals(RateLimitOutcome.STALE_POLICY_DENY, decision.outcome());
        assertTrue(!decision.allowed());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
