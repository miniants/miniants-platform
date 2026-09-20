package cn.miniants.platform.ratelimit.policy;

import cn.miniants.platform.ratelimit.RateLimitAlgorithm;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositeRateLimitPolicyRegistryTest {

    @Test
    void dynamicOverridesYamlAndBuiltins() {
        RateLimitPolicy builtin = policy("shared", 1);
        RateLimitPolicy yaml = policy("auth.login", 5);
        CompositeRateLimitPolicyRegistry registry = new CompositeRateLimitPolicyRegistry(
                Map.of("shared", builtin),
                Map.of("auth.login", yaml, "shared", policy("shared", 2)));

        assertEquals(5, registry.require("auth.login").limit());
        assertEquals(2, registry.require("shared").limit());

        registry.publishDynamicPolicies(Map.of("auth.login", policy("auth.login", 20)), 3L);
        assertEquals(20, registry.require("auth.login").limit());
        assertEquals(2, registry.require("shared").limit());
        assertEquals(3L, registry.revision());
        assertTrue(registry.snapshotLoadedAt().getEpochSecond() > 0);
    }

    @Test
    void neverLoadedDynamicIsNotStale() {
        CompositeRateLimitPolicyRegistry registry = new CompositeRateLimitPolicyRegistry(List.of());
        assertFalse(registry.isSnapshotStale(Duration.ofSeconds(1)));
    }

    @Test
    void lowerRevisionIsRejectedAndSameRevisionOnlyTouchesFreshness() {
        CompositeRateLimitPolicyRegistry registry = new CompositeRateLimitPolicyRegistry(List.of());
        registry.publishDynamicPolicies(Map.of("auth.login", policy("auth.login", 10)), 5L);
        Instant first = registry.lastSuccessfulRefreshAt();

        assertEquals(DynamicPolicyApplyResult.REJECTED_STALE,
                registry.applyDynamicRevision(Map.of("auth.login", policy("auth.login", 99)), 4L, Instant.now()));
        assertEquals(10, registry.require("auth.login").limit());

        Instant later = first.plusSeconds(3);
        assertEquals(DynamicPolicyApplyResult.TOUCHED,
                registry.applyDynamicRevision(Map.of("auth.login", policy("auth.login", 99)), 5L, later));
        assertEquals(10, registry.require("auth.login").limit());
        assertEquals(later, registry.lastSuccessfulRefreshAt());
    }

    private static RateLimitPolicy policy(String code, long limit) {
        return RateLimitPolicy.builder()
                .policyCode(code)
                .algorithm(RateLimitAlgorithm.GCRA)
                .limit(limit)
                .period(Duration.ofSeconds(1))
                .build();
    }
}
