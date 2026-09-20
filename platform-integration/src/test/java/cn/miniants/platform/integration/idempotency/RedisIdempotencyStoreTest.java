package cn.miniants.platform.integration.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedisIdempotencyStoreTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private RedisIdempotencyStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        store = new RedisIdempotencyStore(redis, "platform:idem:");
    }

    @Test
    void firstBeginWinsAndSecondIsRejected() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true, false);

        assertTrue(store.tryBegin("pay:1", Duration.ofMinutes(1)));
        assertFalse(store.tryBegin("pay:1", Duration.ofMinutes(1)));

        verify(values, org.mockito.Mockito.times(2))
                .setIfAbsent(eq("platform:idem:pay:1"), eq("1"), eq(Duration.ofMinutes(1)));
    }

    @Test
    void missingTtlIsRejected() {
        assertFalse(store.tryBegin("pay:1", null));
        assertFalse(store.tryBegin("pay:2", Duration.ZERO));

        verifyNoInteractions(redis);
    }

    @Test
    void blankKeyNeverTouchesRedis() {
        assertFalse(store.tryBegin("  ", Duration.ofMinutes(1)));
        assertFalse(store.tryBegin(null, Duration.ofMinutes(1)));

        verifyNoInteractions(redis);
    }
}
