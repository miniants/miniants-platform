package cn.miniants.platform.queue;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContinuousQueueRequeueTest {

    @Test
    @SuppressWarnings("unchecked")
    void manualRequeueNeverDeletesANewLockAcquiredAfterTheUnlockedCheck() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        ZSetOperations<String, String> zsets = mock(ZSetOperations.class);
        ListOperations<String, String> lists = mock(ListOperations.class);
        SetOperations<String, String> sets = mock(SetOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(redis.opsForZSet()).thenReturn(zsets);
        when(redis.opsForList()).thenReturn(lists);
        when(redis.opsForSet()).thenReturn(sets);
        when(hashes.entries("test:jobs:job:job-1")).thenReturn(Map.of(
                QueueJobFields.JOB_ID, "job-1",
                QueueJobFields.STATUS, QueueJobStatus.PROCESSING));
        AtomicReference<String> lockOwner = new AtomicReference<>();
        when(redis.hasKey("test:jobs:lock:job-1")).thenAnswer(invocation -> {
            lockOwner.set("new-owner");
            return false;
        });
        ContinuousQueue queue = ContinuousQueue.builder(
                        redis, "jobs", new PrefixedQueueKeyFactory("test:"))
                .handler(jobId -> {
                })
                .build();

        queue.requeue("job-1");

        assertThat(lockOwner).hasValue("new-owner");
        verify(redis, never()).delete(anyString());
    }
}
