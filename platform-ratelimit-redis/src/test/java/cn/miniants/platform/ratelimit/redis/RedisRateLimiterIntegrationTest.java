package cn.miniants.platform.ratelimit.redis;

import cn.miniants.platform.ratelimit.RateLimitAlgorithm;
import cn.miniants.platform.ratelimit.RateLimitBackend;
import cn.miniants.platform.ratelimit.RateLimitDecision;
import cn.miniants.platform.ratelimit.RateLimitOutcome;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.RateLimitRequest;
import cn.miniants.platform.ratelimit.StoreFailurePolicy;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class RedisRateLimiterIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final RedisContainer REDIS = new RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME.withTag("7.2-alpine"));

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redis;
    private RedisRateLimiter limiter;

    @BeforeEach
    void connect() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getFirstMappedPort());
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        limiter = new RedisRateLimiter(redis, "test:ratelimit:");
    }

    @AfterEach
    void disconnect() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void gcraAllowsBurstThenDenies() {
        RateLimitPolicy policy = RateLimitPolicy.builder()
                .policyCode("gcra.it")
                .algorithm(RateLimitAlgorithm.GCRA)
                .limit(5)
                .period(Duration.ofSeconds(5))
                .burst(5)
                .storeFailurePolicy(StoreFailurePolicy.DENY)
                .build();
        RateLimitRequest request = RateLimitRequest.of("gcra.it", "burst-user");

        for (int i = 0; i < 5; i++) {
            RateLimitDecision decision = limiter.acquire(request, policy);
            assertTrue(decision.allowed(), "第 " + (i + 1) + " 次应放行");
            assertEquals(RateLimitBackend.REDIS, decision.backend());
            assertEquals(RateLimitOutcome.ALLOWED, decision.outcome());
        }
        RateLimitDecision denied = limiter.acquire(request, policy);
        assertFalse(denied.allowed());
        assertEquals(RateLimitOutcome.DENIED, denied.outcome());
        assertTrue(denied.retryAfter().toMillis() >= 1L);
    }

    @Test
    void slidingWindowDeniesOverLimit() {
        RateLimitPolicy policy = RateLimitPolicy.builder()
                .policyCode("sw.it")
                .algorithm(RateLimitAlgorithm.SLIDING_WINDOW)
                .limit(3)
                .period(Duration.ofSeconds(30))
                .storeFailurePolicy(StoreFailurePolicy.DENY)
                .build();
        RateLimitRequest request = RateLimitRequest.of("sw.it", "sw-user");

        assertTrue(limiter.acquire(request, policy).allowed());
        assertTrue(limiter.acquire(request, policy).allowed());
        assertTrue(limiter.acquire(request, policy).allowed());
        RateLimitDecision denied = limiter.acquire(request, policy);
        assertFalse(denied.allowed());
        assertEquals(RateLimitOutcome.DENIED, denied.outcome());
    }

    @Test
    void concurrentAcquiresRespectLimit() throws Exception {
        RateLimitPolicy policy = RateLimitPolicy.builder()
                .policyCode("conc.it")
                .algorithm(RateLimitAlgorithm.SLIDING_WINDOW)
                .limit(20)
                .period(Duration.ofSeconds(60))
                .storeFailurePolicy(StoreFailurePolicy.DENY)
                .build();
        RateLimitRequest request = RateLimitRequest.of("conc.it", "concurrent");

        int threads = 40;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                if (limiter.acquire(request, policy).allowed()) {
                    allowed.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(15, TimeUnit.SECONDS);
        }
        pool.shutdownNow();
        assertEquals(20, allowed.get());
    }
}
