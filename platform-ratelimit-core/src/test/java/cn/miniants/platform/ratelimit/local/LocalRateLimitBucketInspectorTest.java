package cn.miniants.platform.ratelimit.local;

import cn.miniants.platform.ratelimit.MutableRateLimitClock;
import cn.miniants.platform.ratelimit.RateLimitAlgorithm;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.RateLimitRequest;
import cn.miniants.platform.ratelimit.StoreFailurePolicy;
import cn.miniants.platform.ratelimit.policy.CompositeRateLimitPolicyRegistry;
import cn.miniants.platform.ratelimit.spi.RateLimitBucketSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalRateLimitBucketInspectorTest {

    @Test
    void inspectListsPlainSubjectAfterAcquire() {
        MutableRateLimitClock clock = new MutableRateLimitClock(1_000_000L);
        RateLimitPolicy policy = RateLimitPolicy.builder()
                .policyCode("auth.login")
                .algorithm(RateLimitAlgorithm.GCRA)
                .limit(2)
                .period(Duration.ofSeconds(60))
                .burst(2)
                .storeFailurePolicy(StoreFailurePolicy.DENY)
                .build();
        CompositeLocalRateLimiter limiter = new CompositeLocalRateLimiter(clock);
        limiter.acquire(RateLimitRequest.of("auth.login", "127.0.0.1"), policy);

        LocalRateLimitBucketInspector inspector = new LocalRateLimitBucketInspector(
                limiter, new CompositeRateLimitPolicyRegistry(List.of(policy)), clock);
        RateLimitBucketSnapshot snapshot = inspector.inspect(50);

        assertEquals("local", snapshot.backend());
        assertEquals(1, snapshot.buckets().size());
        assertEquals("auth.login", snapshot.buckets().getFirst().policyCode());
        assertEquals("127.0.0.1", snapshot.buckets().getFirst().subject());
        assertTrue(snapshot.buckets().getFirst().remaining() < 2);
    }
}
