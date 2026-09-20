package cn.miniants.platform.ratelimit.local;

import cn.miniants.platform.ratelimit.MutableRateLimitClock;
import cn.miniants.platform.ratelimit.RateLimitAlgorithm;
import cn.miniants.platform.ratelimit.RateLimitDecision;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.RateLimitRequest;
import cn.miniants.platform.ratelimit.StoreFailurePolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlidingWindowRateLimiterTest {

    private static RateLimitPolicy policy(long limit, Duration period) {
        return RateLimitPolicy.builder()
                .policyCode("sw.test")
                .algorithm(RateLimitAlgorithm.SLIDING_WINDOW)
                .limit(limit)
                .period(period)
                .storeFailurePolicy(StoreFailurePolicy.DENY)
                .build();
    }

    @Test
    void exactlyNAllowsThenDeny() {
        MutableRateLimitClock clock = new MutableRateLimitClock(0L);
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(clock);
        RateLimitPolicy policy = policy(3, Duration.ofSeconds(10));
        RateLimitRequest request = RateLimitRequest.of("sw.test", "u1");

        assertTrue(limiter.acquire(request, policy).allowed());
        assertTrue(limiter.acquire(request, policy).allowed());
        RateLimitDecision third = limiter.acquire(request, policy);
        assertTrue(third.allowed());
        assertEquals(0, third.remaining());

        RateLimitDecision denied = limiter.acquire(request, policy);
        assertFalse(denied.allowed());
        assertEquals(0, denied.remaining());
        assertTrue(denied.retryAfter().toMillis() > 0);
        assertEquals(denied.resetAt().toEpochMilli(), clock.currentTimeMillis() + denied.retryAfter().toMillis());
    }

    @Test
    void windowBoundaryFreesOldest() {
        MutableRateLimitClock clock = new MutableRateLimitClock(1_000L);
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(clock);
        RateLimitPolicy policy = policy(2, Duration.ofMillis(100));
        RateLimitRequest request = RateLimitRequest.of("sw.test", "edge");

        assertTrue(limiter.acquire(request, policy).allowed());
        clock.advanceMillis(50);
        assertTrue(limiter.acquire(request, policy).allowed());
        assertFalse(limiter.acquire(request, policy).allowed());

        // 再过 50ms：第一条过期，应能再进 1 次
        clock.advanceMillis(50);
        RateLimitDecision after = limiter.acquire(request, policy);
        assertTrue(after.allowed());
        assertEquals(0, after.remaining());
        assertFalse(limiter.acquire(request, policy).allowed());
    }

    @Test
    void retryAfterMatchesOldestExpiryForCost() {
        MutableRateLimitClock clock = new MutableRateLimitClock(0L);
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(clock);
        RateLimitPolicy policy = policy(5, Duration.ofMillis(1_000));
        RateLimitRequest request = RateLimitRequest.of("sw.test", "cost");

        assertTrue(limiter.acquire(RateLimitRequest.of("sw.test", "cost", 3), policy).allowed());
        clock.advanceMillis(200);
        assertTrue(limiter.acquire(RateLimitRequest.of("sw.test", "cost", 2), policy).allowed());

        RateLimitDecision denied = limiter.acquire(RateLimitRequest.of("sw.test", "cost", 2), policy);
        assertFalse(denied.allowed());
        // 需要腾出 2：第一条 cost=3 在 t=0，过期于 1000；当前 t=200 → retry=800
        assertEquals(800L, denied.retryAfter().toMillis());

        clock.advanceMillis(800);
        assertTrue(limiter.acquire(RateLimitRequest.of("sw.test", "cost", 2), policy).allowed());
    }

    @Test
    void costLargerThanLimitDenied() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(new MutableRateLimitClock(0L));
        RateLimitPolicy policy = policy(3, Duration.ofSeconds(1));
        RateLimitDecision decision = limiter.acquire(RateLimitRequest.of("sw.test", "big", 4), policy);
        assertFalse(decision.allowed());
    }

    @Test
    void wrongAlgorithmRejected() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(new MutableRateLimitClock(0L));
        RateLimitPolicy gcra = RateLimitPolicy.builder()
                .policyCode("x")
                .algorithm(RateLimitAlgorithm.GCRA)
                .limit(1)
                .period(Duration.ofSeconds(1))
                .build();
        assertThrows(IllegalArgumentException.class,
                () -> limiter.acquire(RateLimitRequest.of("x", "s"), gcra));
    }

    @Test
    void compositeDispatchesByAlgorithm() {
        MutableRateLimitClock clock = new MutableRateLimitClock(0L);
        CompositeLocalRateLimiter composite = new CompositeLocalRateLimiter(clock);

        RateLimitPolicy gcra = policy(1, Duration.ofSeconds(1));
        // rebuild as GCRA
        gcra = RateLimitPolicy.builder()
                .policyCode("c.gcra")
                .algorithm(RateLimitAlgorithm.GCRA)
                .limit(1)
                .period(Duration.ofSeconds(1))
                .burst(1)
                .build();
        RateLimitPolicy sliding = policy(1, Duration.ofSeconds(1));

        assertTrue(composite.acquire(RateLimitRequest.of("c.gcra", "a"), gcra).allowed());
        assertFalse(composite.acquire(RateLimitRequest.of("c.gcra", "a"), gcra).allowed());

        assertTrue(composite.acquire(RateLimitRequest.of("sw.test", "a"), sliding).allowed());
        assertFalse(composite.acquire(RateLimitRequest.of("sw.test", "a"), sliding).allowed());
    }
}
