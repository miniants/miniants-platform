package cn.miniants.platform.integration.idempotency;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Redis 幂等标记，多实例下有效。
 */
public class RedisIdempotencyStore implements IdempotencyStore {

    private final StringRedisTemplate redis;
    private final String keyPrefix;

    public RedisIdempotencyStore(StringRedisTemplate redis, String keyPrefix) {
        this.redis = redis;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public boolean tryBegin(String key, Duration ttl) {
        if (key == null || key.isBlank()) {
            return false;
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return false;
        }
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(keyPrefix + key, "1", ttl));
    }
}
