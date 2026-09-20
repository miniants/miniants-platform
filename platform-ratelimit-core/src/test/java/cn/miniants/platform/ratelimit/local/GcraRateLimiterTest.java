package cn.miniants.platform.ratelimit.local;

import cn.miniants.platform.core.error.PlatformCodes;
import cn.miniants.platform.ratelimit.MutableRateLimitClock;
import cn.miniants.platform.ratelimit.RateLimitAlgorithm;
import cn.miniants.platform.ratelimit.RateLimitDecision;
import cn.miniants.platform.ratelimit.RateLimitExceededException;
import cn.miniants.platform.ratelimit.RateLimitOutcome;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.RateLimitRequest;
import cn.miniants.platform.ratelimit.StoreFailurePolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GcraRateLimiterTest {

    private static RateLimitPolicy policy(long limit, Duration period, long burst) {
        return RateLimitPolicy.builder()
                .policyCode("gcra.test")
                .algorithm(RateLimitAlgorithm.GCRA)
                .limit(limit)
                .period(period)
                .burst(burst)
                .storeFailurePolicy(StoreFailurePolicy.DENY)
                .enabled(true)
                .version(1)
                .build();
    }

    @Test
    void allowThenDenyAtSustainedRate() {
        MutableRateLimitClock clock = new MutableRateLimitClock(1_000_000L);
        GcraRateLimiter limiter = new GcraRateLimiter(clock);
        // 10 / 10s → T=1s；burst=1 → 首发后需等 1s
        RateLimitPolicy policy = policy(10, Duration.ofSeconds(10), 1);
        RateLimitRequest request = RateLimitRequest.of("gcra.test", "user-1");

        RateLimitDecision first = limiter.acquire(request, policy);
        assertTrue(first.allowed());
        assertEquals(0, first.remaining());
        assertEquals(RateLimitOutcome.ALLOWED, first.outcome());
        assertTrue(first.resetAt().toEpochMilli() >= clock.currentTimeMillis());

        RateLimitDecision denied = limiter.acquire(request, policy);
        assertFalse(denied.allowed());
        assertEquals(RateLimitOutcome.DENIED, denied.outcome());
        assertTrue(denied.retryAfter().toMillis() >= 1L);
        assertEquals(clock.currentTimeMillis() + denied.retryAfter().toMillis(),
                denied.resetAt().toEpochMilli());

        clock.advanceMillis(denied.retryAfter().toMillis());
        assertTrue(limiter.acquire(request, policy).allowed());
    }

    @Test
    void burstAllowsImmediateBatch() {
        MutableRateLimitClock clock = new MutableRateLimitClock(0L);
        GcraRateLimiter limiter = new GcraRateLimiter(clock);
        RateLimitPolicy policy = policy(5, Duration.ofSeconds(5), 5);
        RateLimitRequest request = RateLimitRequest.of("gcra.test", "burst");

        for (int i = 0; i < 5; i++) {
            RateLimitDecision decision = limiter.acquire(request, policy);
            assertTrue(decision.allowed(), "第 " + (i + 1) + " 次应放行");
            assertEquals(4L - i, decision.remaining());
        }
        RateLimitDecision denied = limiter.acquire(request, policy);
        assertFalse(denied.allowed());
        assertTrue(denied.retryAfter().toMillis() > 0);
    }

    @Test
    void costConsumesMultipleTokens() {
        MutableRateLimitClock clock = new MutableRateLimitClock(0L);
        GcraRateLimiter limiter = new GcraRateLimiter(clock);
        RateLimitPolicy policy = policy(10, Duration.ofSeconds(10), 10);
        RateLimitRequest heavy = RateLimitRequest.of("gcra.test", "cost", 4);

        RateLimitDecision first = limiter.acquire(heavy, policy);
        assertTrue(first.allowed());
        assertEquals(6, first.remaining());

        RateLimitDecision second = limiter.acquire(RateLimitRequest.of("gcra.test", "cost", 6), policy);
        assertTrue(second.allowed());
        assertEquals(0, second.remaining());

        assertFalse(limiter.acquire(RateLimitRequest.of("gcra.test", "cost", 1), policy).allowed());
    }

    @Test
    void requireThrowsWithDecision() {
        MutableRateLimitClock clock = new MutableRateLimitClock(0L);
        GcraRateLimiter limiter = new GcraRateLimiter(clock);
        RateLimitPolicy policy = policy(1, Duration.ofSeconds(1), 1);
        RateLimitRequest request = RateLimitRequest.of("gcra.test", "req");

        assertTrue(limiter.require(request, policy).allowed());
        RateLimitExceededException ex = assertThrows(
                RateLimitExceededException.class, () -> limiter.require(request, policy));
        assertEquals(PlatformCodes.TOO_MANY_REQUESTS, ex.errorCode());
        assertTrue(ex.getMessage().contains("请求过于频繁"));
        assertFalse(ex.decision().allowed());
        assertTrue(ex.retryAfter().toMillis() >= 1L);
    }

    @Test
    void illegalPolicyAlgorithmRejected() {
        GcraRateLimiter limiter = new GcraRateLimiter(new MutableRateLimitClock(0L));
        RateLimitPolicy sliding = RateLimitPolicy.builder()
                .policyCode("x")
                .algorithm(RateLimitAlgorithm.SLIDING_WINDOW)
                .limit(1)
                .period(Duration.ofSeconds(1))
                .build();
        assertThrows(IllegalArgumentException.class,
                () -> limiter.acquire(RateLimitRequest.of("x", "s"), sliding));
    }

    @Test
    void disabledPolicyAlwaysAllows() {
        GcraRateLimiter limiter = new GcraRateLimiter(new MutableRateLimitClock(0L));
        RateLimitPolicy policy = RateLimitPolicy.builder()
                .policyCode("off")
                .algorithm(RateLimitAlgorithm.GCRA)
                .limit(1)
                .period(Duration.ofSeconds(1))
                .enabled(false)
                .build();
        RateLimitRequest request = RateLimitRequest.of("off", "s");
        assertTrue(limiter.acquire(request, policy).allowed());
        assertTrue(limiter.acquire(request, policy).allowed());
    }

    @Test
    void subjectsAreIsolated() {
        MutableRateLimitClock clock = new MutableRateLimitClock(0L);
        GcraRateLimiter limiter = new GcraRateLimiter(clock);
        RateLimitPolicy policy = policy(1, Duration.ofSeconds(10), 1);
        assertTrue(limiter.acquire(RateLimitRequest.of("gcra.test", "a"), policy).allowed());
        assertTrue(limiter.acquire(RateLimitRequest.of("gcra.test", "b"), policy).allowed());
        assertFalse(limiter.acquire(RateLimitRequest.of("gcra.test", "a"), policy).allowed());
    }
}
