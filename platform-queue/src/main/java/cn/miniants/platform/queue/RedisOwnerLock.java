package cn.miniants.platform.queue;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

/**
 * 只负责带 owner token 的 Redis 租约获取与条件释放。
 */
final class RedisOwnerLock {

    private static final RedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    RedisOwnerLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    boolean tryAcquire(String key, String ownerToken, Duration ttl) {
        if (ownerToken == null || ownerToken.isBlank()) {
            throw new IllegalArgumentException("锁 owner token 不能为空");
        }
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, ownerToken, ttl));
    }

    boolean release(String key, String ownerToken) {
        if (ownerToken == null || ownerToken.isBlank()) {
            throw new IllegalArgumentException("锁 owner token 不能为空");
        }
        return Long.valueOf(1L).equals(redis.execute(RELEASE, List.of(key), ownerToken));
    }
}
