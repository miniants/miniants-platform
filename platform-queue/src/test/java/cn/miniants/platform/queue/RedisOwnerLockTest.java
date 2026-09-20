package cn.miniants.platform.queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisOwnerLockTest {

    private static final String KEY = "queue:test:lock:job-1";
    private static final Duration LEASE = Duration.ofSeconds(1);

    private final AtomicReference<String> redisValue = new AtomicReference<>();
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private RedisOwnerLock lock;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(eq(KEY), anyString(), eq(LEASE))).thenAnswer(invocation -> {
            String token = invocation.getArgument(1);
            return redisValue.compareAndSet(null, token);
        });
        when(redis.execute(any(RedisScript.class), eq(List.of(KEY)), anyString())).thenAnswer(invocation -> {
            RedisScript<Long> script = invocation.getArgument(0);
            assertThat(script.getScriptAsString())
                    .contains("redis.call('get', KEYS[1]) == ARGV[1]")
                    .contains("redis.call('del', KEYS[1])");
            String token = invocation.getArgument(2);
            return redisValue.compareAndSet(token, null) ? 1L : 0L;
        });
        lock = new RedisOwnerLock(redis);
    }

    @Test
    void expiredOldOwnerCannotReleaseTheNewOwnersLock() {
        assertThat(lock.tryAcquire(KEY, "old-owner", LEASE)).isTrue();
        redisValue.set(null); // 模拟租约到期
        assertThat(lock.tryAcquire(KEY, "new-owner", LEASE)).isTrue();

        assertThat(lock.release(KEY, "old-owner")).isFalse();
        assertThat(redisValue).hasValue("new-owner");

        assertThat(lock.release(KEY, "new-owner")).isTrue();
        assertThat(redisValue).hasValue(null);
    }
}
