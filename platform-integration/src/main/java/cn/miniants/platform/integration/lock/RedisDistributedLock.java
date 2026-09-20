package cn.miniants.platform.integration.lock;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

/**
 * Redis 互斥锁，多实例下有效。
 */
public class RedisDistributedLock implements DistributedLock {

    /** 释放必须比对令牌，否则会删掉租约到期后别人刚抢到的锁。 */
    private static final RedisScript<Long> UNLOCK = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """, Long.class);

    private final LockTokens tokens = new LockTokens();
    private final StringRedisTemplate redis;
    private final String keyPrefix;

    public RedisDistributedLock(StringRedisTemplate redis, String keyPrefix) {
        this.redis = redis;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public boolean tryLock(String key, Duration lease) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String token = LockTokens.newToken();
        Boolean acquired = redis.opsForValue().setIfAbsent(
                keyPrefix + key, token, Duration.ofMillis(DistributedLock.leaseMillis(lease)));
        if (!Boolean.TRUE.equals(acquired)) {
            return false;
        }
        tokens.hold(key, token);
        return true;
    }

    @Override
    public void unlock(String key) {
        String token = tokens.release(key);
        if (token == null) {
            return;
        }
        redis.execute(UNLOCK, List.of(keyPrefix + key), token);
    }
}
