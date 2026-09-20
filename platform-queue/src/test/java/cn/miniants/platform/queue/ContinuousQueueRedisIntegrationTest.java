package cn.miniants.platform.queue;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class ContinuousQueueRedisIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final RedisContainer REDIS = new RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME.withTag("7.2-alpine"));

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redis;

    @BeforeEach
    void connect() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getFirstMappedPort());
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
    }

    @AfterEach
    void disconnect() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void enqueueBrpopAndDelayLuaPromote() {
        QueueKeyFactory keys = new PrefixedQueueKeyFactory("test:queue:");
        ContinuousQueue queue = ContinuousQueue.builder(redis, "iot", keys)
                .handler(jobId -> {
                })
                .build();

        String jobId = queue.enqueue(Map.of("kind", "ping"));
        String polled = queue.pollReady(3, TimeUnit.SECONDS);
        assertThat(polled).isEqualTo(jobId);

        RedisDelayZSet delay = new RedisDelayZSet(
                redis, keys.delay("iot"), keys.ready("iot"));
        delay.zadd("delayed-job", System.currentTimeMillis() - 1_000L);
        assertThat(delay.promoteFirstDue(System.currentTimeMillis())).isTrue();
        assertThat(redis.opsForList().leftPop(keys.ready("iot"))).isEqualTo("delayed-job");
    }

    @Test
    void ownerLockLuaPreventsCrossTokenRelease() {
        RedisOwnerLock lock = new RedisOwnerLock(redis);
        String key = "test:queue:iot:lock:job-1";
        Duration lease = Duration.ofSeconds(30);

        assertThat(lock.tryAcquire(key, "owner-a", lease)).isTrue();
        assertThat(lock.tryAcquire(key, "owner-b", lease)).isFalse();
        assertThat(lock.release(key, "owner-b")).isFalse();
        assertThat(lock.release(key, "owner-a")).isTrue();
        assertThat(lock.tryAcquire(key, "owner-b", lease)).isTrue();
    }
}
