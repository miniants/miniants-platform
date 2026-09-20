package cn.miniants.platform.ratelimit.redis;

import cn.miniants.platform.ratelimit.RateLimitAlgorithm;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.spi.RateLimitBucketInspector;
import cn.miniants.platform.ratelimit.spi.RateLimitBucketSnapshot;
import cn.miniants.platform.ratelimit.spi.RateLimitBucketView;
import cn.miniants.platform.ratelimit.spi.RateLimitPolicyRegistry;
import cn.miniants.platform.ratelimit.support.RateLimitBucketMath;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * SCAN 计数键并按策略算法换算剩余额度。禁止 KEYS / FLUSHALL。
 */
public class RedisRateLimitBucketInspector implements RateLimitBucketInspector {

    private final StringRedisTemplate redis;
    private final String keyPrefix;
    private final RateLimitPolicyRegistry registry;

    public RedisRateLimitBucketInspector(
            StringRedisTemplate redis, String keyPrefix, RateLimitPolicyRegistry registry) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public RateLimitBucketSnapshot inspect(int maxBuckets) {
        int max = Math.max(1, Math.min(maxBuckets, 2000));
        long nowMs = currentRedisTimeMillis();
        List<RateLimitBucketView> rows = new ArrayList<>();
        boolean truncated = false;
        ScanOptions options = ScanOptions.scanOptions()
                .match(RateLimitRedisKeys.counterScanPattern(keyPrefix))
                .count(100)
                .build();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                if (rows.size() >= max) {
                    truncated = true;
                    break;
                }
                RateLimitBucketView view = readOne(key, nowMs);
                if (view != null) {
                    rows.add(view);
                }
            }
        }
        return new RateLimitBucketSnapshot(Instant.ofEpochMilli(nowMs), "redis", truncated, List.copyOf(rows));
    }

    private RateLimitBucketView readOne(String redisKey, long nowMs) {
        RateLimitRedisKeys.CounterKey parsed = RateLimitRedisKeys.parseCounter(keyPrefix, redisKey);
        if (parsed == null) {
            return null;
        }
        RateLimitPolicy policy = registry.find(parsed.policyCode()).orElse(null);
        DataType type = redis.type(redisKey);
        if (type == DataType.ZSET) {
            return readSlidingWindow(redisKey, parsed, policy, nowMs);
        }
        if (type == DataType.STRING) {
            return readGcra(redisKey, parsed, policy, nowMs);
        }
        return null;
    }

    private RateLimitBucketView readGcra(
            String redisKey, RateLimitRedisKeys.CounterKey parsed, RateLimitPolicy policy, long nowMs) {
        if (policy == null || policy.algorithm() != RateLimitAlgorithm.GCRA) {
            return null;
        }
        String raw = redis.opsForValue().get(redisKey);
        double tat = raw == null || raw.isBlank() ? nowMs : parseDouble(raw, nowMs);
        RateLimitBucketMath.Peek peek = RateLimitBucketMath.peekGcra(
                nowMs, policy.limit(), policy.burst(), policy.period().toMillis(), tat);
        return toView(parsed, policy, peek);
    }

    private RateLimitBucketView readSlidingWindow(
            String redisKey, RateLimitRedisKeys.CounterKey parsed, RateLimitPolicy policy, long nowMs) {
        if (policy == null || policy.algorithm() != RateLimitAlgorithm.SLIDING_WINDOW) {
            return null;
        }
        long periodMs = policy.period().toMillis();
        long cutoff = nowMs - periodMs;
        Long used = redis.opsForZSet().count(redisKey, cutoff, Double.POSITIVE_INFINITY);
        long usedCount = used == null ? 0L : used;
        Set<ZSetOperations.TypedTuple<String>> oldest = redis.opsForZSet()
                .rangeByScoreWithScores(redisKey, cutoff, Double.POSITIVE_INFINITY, 0, 1);
        Long oldestAt = null;
        if (oldest != null && !oldest.isEmpty()) {
            ZSetOperations.TypedTuple<String> first = oldest.iterator().next();
            if (first.getScore() != null) {
                oldestAt = first.getScore().longValue();
            }
        }
        RateLimitBucketMath.Peek peek = RateLimitBucketMath.peekSlidingWindow(
                nowMs, policy.limit(), periodMs, usedCount, oldestAt);
        return toView(parsed, policy, peek);
    }

    private static RateLimitBucketView toView(
            RateLimitRedisKeys.CounterKey parsed, RateLimitPolicy policy, RateLimitBucketMath.Peek peek) {
        return new RateLimitBucketView(
                parsed.policyCode(),
                parsed.subject(),
                policy.algorithm().name(),
                policy.limit(),
                peek.remaining(),
                peek.retryAfterMs(),
                peek.resetAtMs());
    }

    private long currentRedisTimeMillis() {
        try {
            Long time = redis.getConnectionFactory() == null
                    ? null
                    : redis.execute((RedisCallback<Long>) connection -> connection.serverCommands().time());
            if (time != null && time > 0L) {
                return time;
            }
        } catch (RuntimeException ignored) {
            // 展示用，回退本机时钟
        }
        return System.currentTimeMillis();
    }

    private static double parseDouble(String raw, long fallback) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
