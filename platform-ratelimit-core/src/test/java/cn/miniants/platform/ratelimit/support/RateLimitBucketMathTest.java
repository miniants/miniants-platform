package cn.miniants.platform.ratelimit.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitBucketMathTest {

    @Test
    void gcraFreshBucketHasFullRemaining() {
        RateLimitBucketMath.Peek peek = RateLimitBucketMath.peekGcra(1_000_000L, 20, 20, 60_000L, 1_000_000L);
        assertEquals(20L, peek.remaining());
        assertEquals(0L, peek.retryAfterMs());
    }

    @Test
    void gcraEmptyBurstReportsRetry() {
        long now = 1_000_000L;
        double tat = now + 60_000L;
        RateLimitBucketMath.Peek peek = RateLimitBucketMath.peekGcra(now, 1, 1, 60_000L, tat);
        assertEquals(0L, peek.remaining());
        assertTrue(peek.retryAfterMs() >= 1L);
    }

    @Test
    void slidingWindowUsedEqualsLimit() {
        RateLimitBucketMath.Peek peek = RateLimitBucketMath.peekSlidingWindow(1_000L, 3, 10_000L, 3, 500L);
        assertEquals(0L, peek.remaining());
        assertTrue(peek.retryAfterMs() >= 1L);
    }
}
