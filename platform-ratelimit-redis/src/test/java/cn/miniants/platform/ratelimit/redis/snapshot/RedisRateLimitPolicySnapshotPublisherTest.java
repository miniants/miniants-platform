package cn.miniants.platform.ratelimit.redis.snapshot;

import cn.miniants.platform.core.json.Jsons;
import cn.miniants.platform.ratelimit.RateLimitAlgorithm;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.config.RateLimitProperties;
import cn.miniants.platform.ratelimit.policy.CompositeRateLimitPolicyRegistry;
import cn.miniants.platform.ratelimit.policy.DynamicPolicyApplyResult;
import cn.miniants.platform.ratelimit.snapshot.RateLimitPolicySnapshotCodec;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class RedisRateLimitPolicySnapshotPublisherTest {

    @Container
    @SuppressWarnings("resource")
    static final RedisContainer REDIS = new RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME.withTag("7.2-alpine"));

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redis;
    private RedisRateLimitPolicySnapshotPublisher publisher;
    private RateLimitPolicySnapshotRefreshService refreshService;
    private CompositeRateLimitPolicyRegistry registry;

    @BeforeEach
    void connect() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getFirstMappedPort());
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        RateLimitProperties properties = new RateLimitProperties();
        properties.setKeyPrefix("test:ratelimit:");
        properties.setSnapshotTtl(Duration.ofMinutes(5));
        publisher = new RedisRateLimitPolicySnapshotPublisher(redis, properties);
        registry = new CompositeRateLimitPolicyRegistry(List.of());
        refreshService = new RateLimitPolicySnapshotRefreshService(
                redis, new RateLimitPolicySnapshotCodec(Jsons.mapper()), registry, properties);
    }

    @AfterEach
    void disconnect() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void casRejectsLowerRevisionAndTouchesSameRevision() {
        RateLimitPolicy policy = RateLimitPolicy.builder()
                .policyCode("auth.login")
                .algorithm(RateLimitAlgorithm.GCRA)
                .limit(10)
                .period(Duration.ofSeconds(1))
                .build();
        RateLimitPolicySnapshotCodec codec = new RateLimitPolicySnapshotCodec(Jsons.mapper());
        assertEquals(1, publisher.publish(3L, codec.encode(3L, List.of(policy))));
        assertEquals(0, publisher.publish(2L, codec.encode(2L, List.of(policy))));
        assertEquals(2, publisher.publish(3L, codec.encode(3L, List.of(policy))));

        assertTrue(refreshService.refreshFromRedis());
        assertEquals(3L, registry.revision());
        assertEquals(DynamicPolicyApplyResult.REJECTED_STALE,
                registry.applyDynamicRevision(codec.toMap(codec.decode(codec.encode(2L, List.of(policy)))), 2L, Instant.now()));
        assertFalse(registry.sourceUnavailable());
    }
}
