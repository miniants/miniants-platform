package cn.miniants.platform.ratelimit.redis;

import cn.miniants.platform.ratelimit.RateLimitAlgorithm;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.RateLimitRequest;
import cn.miniants.platform.ratelimit.StoreFailurePolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitRedisScriptsArgsTest {

    @Test
    void gcraArgsMatchPolicyAndRequest() {
        RateLimitPolicy policy = RateLimitPolicy.builder()
                .policyCode("api.gcra")
                .algorithm(RateLimitAlgorithm.GCRA)
                .limit(10)
                .period(Duration.ofSeconds(60))
                .burst(5)
                .storeFailurePolicy(StoreFailurePolicy.DENY)
                .build();
        RateLimitRequest request = RateLimitRequest.of("api.gcra", "client-a", 2);

        List<String> args = RateLimitRedisScripts.gcraArgs(request, policy);
        assertEquals(List.of("10", "5", "60000", "2", "61000"), args);
    }

    @Test
    void slidingWindowArgsIncludeUniqueMemberPrefix() {
        RateLimitPolicy policy = RateLimitPolicy.builder()
                .policyCode("api.sw")
                .algorithm(RateLimitAlgorithm.SLIDING_WINDOW)
                .limit(3)
                .period(Duration.ofSeconds(10))
                .storeFailurePolicy(StoreFailurePolicy.ALLOW)
                .build();
        RateLimitRequest request = RateLimitRequest.of("api.sw", "client-b", 1);

        List<String> a = RateLimitRedisScripts.slidingWindowArgs(request, policy);
        List<String> b = RateLimitRedisScripts.slidingWindowArgs(request, policy);
        assertEquals("10000", a.get(0));
        assertEquals("3", a.get(1));
        assertEquals("1", a.get(2));
        assertEquals("11000", a.get(3));
        assertTrue(a.get(4).length() > 8);
        assertTrue(!a.get(4).equals(b.get(4)));
    }

    @Test
    void counterKeyUsesPlainSubject() {
        RateLimitPolicy policy = RateLimitPolicy.builder()
                .policyCode("login")
                .algorithm(RateLimitAlgorithm.GCRA)
                .limit(1)
                .period(Duration.ofMinutes(1))
                .build();
        RateLimitRequest request = RateLimitRequest.of("login", "very-long-subject-value");
        String key = RateLimitRedisScripts.counterKey("platform:ratelimit:", request, policy);
        assertEquals("platform:ratelimit:c:login:very-long-subject-value", key);
    }
}

