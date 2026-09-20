package cn.miniants.platform.ratelimit.admin;

import cn.miniants.platform.core.json.Jsons;
import cn.miniants.platform.ratelimit.admin.dto.RateLimitBucketVo;
import cn.miniants.platform.ratelimit.admin.dto.RateLimitBucketsVo;
import cn.miniants.platform.ratelimit.admin.service.RateLimitPolicyAdminService;
import cn.miniants.platform.ratelimit.config.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitBucketHistoryTest {

    private static final Instant OBSERVED = Instant.parse("2026-09-06T01:00:00Z");

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private ZSetOperations<String, String> zset;
    private RateLimitPolicyAdminService adminService;
    private RateLimitProperties rateLimitProperties;
    private RateLimitAdminProperties adminProperties;
    private AtomicLong clock;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        zset = mock(ZSetOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForZSet()).thenReturn(zset);
        adminService = mock(RateLimitPolicyAdminService.class);
        rateLimitProperties = new RateLimitProperties();
        rateLimitProperties.setKeyPrefix("jwy:yjs:ratelimit:");
        adminProperties = new RateLimitAdminProperties();
        adminProperties.getLive().setEnabled(true);
        clock = new AtomicLong(OBSERVED.toEpochMilli());
    }

    @Test
    void rejectsRetentionOverOneHour() {
        adminProperties.getLive().setRetention(Duration.ofHours(1).plusSeconds(1));
        assertThatThrownBy(() -> history())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1h");
    }

    @Test
    void rejectsMoreThanMaxFrames() {
        adminProperties.getLive().setSampleInterval(Duration.ofSeconds(5));
        adminProperties.getLive().setRetention(Duration.ofHours(1));
        assertThatThrownBy(() -> history())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("120");
    }

    @Test
    void acceptsOneHourAtThirtySeconds() {
        assertThat(history()).isNotNull();
    }

    @Test
    void skipsInspectWhenSlotAlreadyTaken() {
        when(values.setIfAbsent(eq("jwy:yjs:ratelimit:live:sample:" + slot()), eq("1"), eq(Duration.ofHours(1))))
                .thenReturn(Boolean.FALSE);

        history().sample();

        verify(adminService, never()).buckets(500);
        verify(zset, never()).add(anyString(), anyString(), anyDouble());
    }

    @Test
    void writesSnapshotAndTrimsOldFrames() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(Boolean.TRUE);
        when(adminService.buckets(500)).thenReturn(snapshot(19));
        when(zset.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        when(zset.removeRangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(1L);
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(true);

        history().sample();

        verify(zset).add(
                eq("jwy:yjs:ratelimit:live:snapshots"),
                eq(Jsons.toJson(snapshot(19))),
                eq((double) OBSERVED.toEpochMilli()));
        verify(zset).removeRangeByScore(
                eq("jwy:yjs:ratelimit:live:snapshots"),
                eq(0.0),
                eq((double) (OBSERVED.toEpochMilli() - Duration.ofHours(1).toMillis() - 1)));
        verify(redis).expire("jwy:yjs:ratelimit:live:snapshots", Duration.ofHours(1));
    }

    @Test
    void doesNotWriteEmptySnapshot() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(Boolean.TRUE);
        RateLimitBucketsVo empty = new RateLimitBucketsVo();
        empty.setObservedAt(OBSERVED);
        empty.setBuckets(List.of());
        when(adminService.buckets(500)).thenReturn(empty);

        history().sample();

        verify(zset, never()).add(anyString(), anyString(), anyDouble());
    }

    @Test
    void listReturnsStoredFramesAndSkipsCorruptJson() {
        RateLimitBucketsVo first = snapshot(19);
        RateLimitBucketsVo second = snapshot(18);
        second.setObservedAt(OBSERVED.plusSeconds(30));
        LinkedHashSet<String> raw = new LinkedHashSet<>();
        raw.add(Jsons.toJson(first));
        raw.add("{not-json");
        raw.add(Jsons.toJson(second));
        when(zset.rangeByScore(eq("jwy:yjs:ratelimit:live:snapshots"), anyDouble(), anyDouble()))
                .thenReturn(raw);

        List<RateLimitBucketsVo> frames = history().list();

        assertThat(frames).hasSize(2);
        assertThat(frames.get(0).getBuckets().getFirst().getRemaining()).isEqualTo(19L);
        assertThat(frames.get(1).getBuckets().getFirst().getRemaining()).isEqualTo(18L);
    }

    @Test
    void listReturnsEmptyWhenRedisFails() {
        when(zset.rangeByScore(anyString(), anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("down"));

        assertThat(history().list()).isEmpty();
    }

    @Test
    void sampleFailureDoesNotPropagate() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("down"));

        history().sample();

        verify(adminService, never()).buckets(500);
    }

    private RateLimitBucketHistory history() {
        return new RateLimitBucketHistory(
                redis, rateLimitProperties, adminProperties, adminService, clock::get);
    }

    private long slot() {
        return OBSERVED.toEpochMilli() / Duration.ofSeconds(30).toMillis();
    }

    private static RateLimitBucketsVo snapshot(long remaining) {
        RateLimitBucketVo bucket = new RateLimitBucketVo();
        bucket.setPolicyCode("auth.login");
        bucket.setSubject("127.0.0.1");
        bucket.setAlgorithm("GCRA");
        bucket.setLimitCount(20);
        bucket.setRemaining(remaining);
        RateLimitBucketsVo vo = new RateLimitBucketsVo();
        vo.setObservedAt(OBSERVED);
        vo.setBackend("redis");
        vo.setBuckets(List.of(bucket));
        return vo;
    }
}
