package cn.miniants.platform.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RateLimitModelTest {

    @Test
    void policyBurstDefaultsToLimit() {
        RateLimitPolicy policy = RateLimitPolicy.builder()
                .policyCode("p")
                .algorithm(RateLimitAlgorithm.GCRA)
                .limit(9)
                .period(Duration.ofSeconds(9))
                .build();
        assertEquals(9, policy.burst());
    }

    @Test
    void requestDefaultCostIsOne() {
        assertEquals(1, RateLimitRequest.of("p", "s").cost());
    }

    @Test
    void rejectsBlankSubject() {
        assertThrows(IllegalArgumentException.class, () -> new RateLimitRequest("p", " "));
        assertThrows(IllegalArgumentException.class, () -> RateLimitRequest.of("p", "s", 0));
    }
}
