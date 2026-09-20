package cn.miniants.platform.ratelimit.policy;

import cn.miniants.platform.ratelimit.RateLimitAlgorithm;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryRateLimitPolicyRegistryTest {

    @Test
    void resolveByCode() {
        RateLimitPolicy policy = RateLimitPolicy.builder()
                .policyCode("auth.login")
                .algorithm(RateLimitAlgorithm.GCRA)
                .limit(10)
                .period(Duration.ofMinutes(1))
                .build();
        InMemoryRateLimitPolicyRegistry registry = new InMemoryRateLimitPolicyRegistry(List.of(policy));

        Optional<RateLimitPolicy> found = registry.find("auth.login");
        assertTrue(found.isPresent());
        assertEquals(10, found.get().limit());
        assertEquals(policy, registry.require("auth.login"));
    }

    @Test
    void missingThrows() {
        InMemoryRateLimitPolicyRegistry registry = new InMemoryRateLimitPolicyRegistry();
        assertTrue(registry.find("missing").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> registry.require("missing"));
    }

    @Test
    void replaceAllUpdatesSnapshot() {
        InMemoryRateLimitPolicyRegistry registry = new InMemoryRateLimitPolicyRegistry();
        registry.put(RateLimitPolicy.builder()
                .policyCode("a")
                .algorithm(RateLimitAlgorithm.SLIDING_WINDOW)
                .limit(1)
                .period(Duration.ofSeconds(1))
                .build());
        registry.replaceAll(List.of(RateLimitPolicy.builder()
                .policyCode("b")
                .algorithm(RateLimitAlgorithm.GCRA)
                .limit(2)
                .period(Duration.ofSeconds(2))
                .build()));
        assertTrue(registry.find("a").isEmpty());
        assertEquals(2, registry.require("b").limit());
        assertEquals(1, registry.snapshot().size());
    }
}
