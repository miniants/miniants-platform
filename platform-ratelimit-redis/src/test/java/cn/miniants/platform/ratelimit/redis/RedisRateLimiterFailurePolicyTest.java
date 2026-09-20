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
class RedisRateLimiterFailurePolicyTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private org.springframework.data.redis.core.ReactiveStringRedisTemplate reactiveRedis;

    @Test
    @SuppressWarnings("unchecked")
    void syncDenyOnStoreFailure() {
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("redis down"));
        RedisRateLimiter limiter = new RedisRateLimiter(redis, "platform:ratelimit:");
        RateLimitPolicy policy = policy(StoreFailurePolicy.DENY);

        RateLimitDecision decision = limiter.acquire(RateLimitRequest.of("p", "s"), policy);
        assertFalse(decision.allowed());
        assertEquals(RateLimitOutcome.STORE_ERROR_DENY, decision.outcome());
        assertEquals(RateLimitBackend.REDIS, decision.backend());
    }

    @Test
    @SuppressWarnings("unchecked")
    void syncAllowOnStoreFailure() {
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("redis down"));
        RedisRateLimiter limiter = new RedisRateLimiter(redis, "platform:ratelimit:");
        RateLimitPolicy policy = policy(StoreFailurePolicy.ALLOW);

        RateLimitDecision decision = limiter.acquire(RateLimitRequest.of("p", "s"), policy);
        assertTrue(decision.allowed());
        assertEquals(RateLimitOutcome.STORE_ERROR_ALLOW, decision.outcome());
        assertEquals(RateLimitBackend.REDIS, decision.backend());
    }

    @Test
    @SuppressWarnings("unchecked")
    void reactiveDenyOnStoreFailure() {
        when(reactiveRedis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.error(new RuntimeException("redis down")));
        ReactiveRedisRateLimiter limiter = new ReactiveRedisRateLimiter(reactiveRedis, "platform:ratelimit:");
        RateLimitPolicy policy = policy(StoreFailurePolicy.DENY);

        RateLimitDecision decision = limiter.acquire(RateLimitRequest.of("p", "s"), policy).block();
        assertFalse(decision.allowed());
        assertEquals(RateLimitOutcome.STORE_ERROR_DENY, decision.outcome());
    }

    @Test
    @SuppressWarnings("unchecked")
    void reactiveAllowOnStoreFailure() {
        when(reactiveRedis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.error(new RuntimeException("redis down")));
        ReactiveRedisRateLimiter limiter = new ReactiveRedisRateLimiter(reactiveRedis, "platform:ratelimit:");
        RateLimitPolicy policy = policy(StoreFailurePolicy.ALLOW);

        RateLimitDecision decision = limiter.acquire(RateLimitRequest.of("p", "s"), policy).block();
        assertTrue(decision.allowed());
        assertEquals(RateLimitOutcome.STORE_ERROR_ALLOW, decision.outcome());
    }

    private static RateLimitPolicy policy(StoreFailurePolicy failurePolicy) {
        return RateLimitPolicy.builder()
                .policyCode("p")
                .algorithm(RateLimitAlgorithm.GCRA)
                .limit(5)
                .period(Duration.ofSeconds(10))
                .burst(5)
                .storeFailurePolicy(failurePolicy)
                .build();
    }
}
