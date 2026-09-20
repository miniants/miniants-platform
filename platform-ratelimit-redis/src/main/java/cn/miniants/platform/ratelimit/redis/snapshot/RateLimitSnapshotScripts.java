package cn.miniants.platform.ratelimit.redis.snapshot;

import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * 快照 revision CAS：仅更高修订覆盖；相同修订续期并再广播。
 */
final class RateLimitSnapshotScripts {

    static final RedisScript<Long> PUBLISH = new DefaultRedisScript<>("""
            local revKey = KEYS[1]
            local snapKey = KEYS[2]
            local channel = KEYS[3]
            local newRev = tonumber(ARGV[1])
            local json = ARGV[2]
            local ttlMs = tonumber(ARGV[3])
            local current = tonumber(redis.call('GET', revKey) or '0')
            if newRev < current then
              return 0
            end
            redis.call('SET', snapKey, json, 'PX', ttlMs)
            redis.call('SET', revKey, tostring(newRev), 'PX', ttlMs)
            redis.call('PUBLISH', channel, tostring(newRev))
            if newRev == current then
              return 2
            end
            return 1
            """, Long.class);

    private RateLimitSnapshotScripts() {
    }
}
