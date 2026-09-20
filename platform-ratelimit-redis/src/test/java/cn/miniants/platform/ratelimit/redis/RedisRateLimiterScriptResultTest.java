package cn.miniants.platform.ratelimit.redis;

import cn.miniants.platform.ratelimit.RateLimitAlgorithm;
import cn.miniants.platform.ratelimit.RateLimitBackend;
import cn.miniants.platform.ratelimit.RateLimitDecision;
import cn.miniants.platform.ratelimit.RateLimitOutcome;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.RateLimitRequest;
import cn.miniants.platform.ratelimit.StoreFailurePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRateLimiterScriptResultTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private org.springframework.data.redis.core.ReactiveStringRedisTemplate reactiveRedis;

    @Test
    @SuppressWarnings("unchecked")
    void syncParsesAllowedAndDeniedTuples() {
        RateLimitPolicy policy = policy();
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(1L, 4L, 0L, 1_700_000_000_000L))
                .thenReturn(List.of(0L, 0L, 250L, 1_700_000_000_250L));

        RedisRateLimiter limiter = new RedisRateLimiter(redis, "platform:ratelimit:");
        RateLimitRequest request = RateLimitRequest.of("p", "s");

        RateLimitDecision allowed = limiter.acquire(request, policy);
        assertTrue(allowed.allowed());
        assertEquals(4L, allowed.remaining());
        assertEquals(RateLimitOutcome.ALLOWED, allowed.outcome());
        assertEquals(RateLimitBackend.REDIS, allowed.backend());

        RateLimitDecision denied = limiter.acquire(request, policy);
        assertFalse(denied.allowed());
        assertEquals(250L, denied.retryAfter().toMillis());
        assertEquals(RateLimitOutcome.DENIED, denied.outcome());
    }

    @Test
    @SuppressWarnings("unchecked")
    void reactiveCollectsFluxElementsAsTuple() {
        when(reactiveRedis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(1L, 3L, 0L, 99L));

        ReactiveRedisRateLimiter limiter = new ReactiveRedisRateLimiter(reactiveRedis, "platform:ratelimit:");
        RateLimitDecision decision = limiter.acquire(RateLimitRequest.of("p", "s"), policy()).block();

        assertTrue(decision.allowed());
        assertEquals(3L, decision.remaining());
        assertEquals(99L, decision.resetAt().toEpochMilli());
        assertEquals(RateLimitBackend.REDIS, decision.backend());
    }

    private static RateLimitPolicy policy() {
        return RateLimitPolicy.builder()
                .policyCode("p")
                .algorithm(RateLimitAlgorithm.GCRA)
                .limit(5)
                .period(Duration.ofSeconds(10))
                .burst(5)
                .storeFailurePolicy(StoreFailurePolicy.DENY)
                .build();
    }
}
