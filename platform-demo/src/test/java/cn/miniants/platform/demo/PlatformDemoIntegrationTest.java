package cn.miniants.platform.demo;

import cn.miniants.platform.integration.idempotency.IdempotencyStore;
import cn.miniants.platform.integration.lock.DistributedLock;
import cn.miniants.platform.integration.secret.SecretProvider;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.RateLimitRequest;
import cn.miniants.platform.ratelimit.spi.RateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = "platform.secret.values.demo-key=from-test")
class PlatformDemoIntegrationTest {

    @Autowired
    private SecretProvider secretProvider;
    @Autowired
    private DistributedLock distributedLock;
    @Autowired
    private IdempotencyStore idempotencyStore;
    @Autowired
    private RateLimiter rateLimiter;

    @Test
    void beansAreWired() {
        assertEquals("from-test", secretProvider.require("demo-key"));
        assertTrue(distributedLock.tryLock("demo", Duration.ZERO));
        distributedLock.unlock("demo");
        assertTrue(idempotencyStore.tryBegin("demo:1", Duration.ofSeconds(5)));
        RateLimitPolicy policy = RateLimitPolicy.builder()
                .policyCode("demo")
                .limit(3)
                .period(Duration.ofSeconds(5))
                .build();
        assertTrue(rateLimiter.acquire(RateLimitRequest.of("demo", "demo"), policy).allowed());
    }
}
