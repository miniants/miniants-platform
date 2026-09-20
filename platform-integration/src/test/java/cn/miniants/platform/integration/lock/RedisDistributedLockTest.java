package cn.miniants.platform.integration.lock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class RedisDistributedLockTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private RedisDistributedLock lock;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        lock = new RedisDistributedLock(redis, "platform:lock:");
    }

    @Test
    void acquiresWithPrefixedKeyAndLease() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        assertTrue(lock.tryLock("pay", Duration.ofSeconds(5)));

        verify(values).setIfAbsent(eq("platform:lock:pay"), anyString(), eq(Duration.ofSeconds(5)));
    }

    @Test
    void missingLeaseFallsBackToDefaultInsteadOfNeverExpiring() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        assertTrue(lock.tryLock("pay", null));
        assertTrue(lock.tryLock("other", Duration.ZERO));

        verify(values).setIfAbsent(eq("platform:lock:pay"), anyString(), eq(DistributedLock.DEFAULT_LEASE));
        verify(values).setIfAbsent(eq("platform:lock:other"), anyString(), eq(DistributedLock.DEFAULT_LEASE));
    }

    @Test
    void heldByOthersIsNotAcquired() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertFalse(lock.tryLock("pay", Duration.ofSeconds(5)));
    }

    @Test
    void blankKeyNeverTouchesRedis() {
        assertFalse(lock.tryLock("  ", Duration.ofSeconds(5)));
        assertFalse(lock.tryLock(null, Duration.ofSeconds(5)));

        verifyNoInteractions(redis);
    }

    @Test
    void releaseComparesTheTokenItAcquiredWith() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        lock.tryLock("pay", Duration.ofSeconds(5));
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(values).setIfAbsent(anyString(), token.capture(), any(Duration.class));

        lock.unlock("pay");

        verify(redis).execute(any(RedisScript.class), eq(List.of("platform:lock:pay")), eq(token.getValue()));
    }

    @Test
    void releasingALockWeDoNotHoldIsANoOp() {
        lock.unlock("pay");

        verify(redis, never()).execute(any(RedisScript.class), any(), any());
    }

    @Test
    void releaseIsNotRepeatable() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        lock.tryLock("pay", Duration.ofSeconds(5));

        lock.unlock("pay");
        lock.unlock("pay");

        verify(redis).execute(any(RedisScript.class), any(), any());
    }
}
