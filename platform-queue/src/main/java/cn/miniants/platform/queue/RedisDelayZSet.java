package cn.miniants.platform.queue;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

/**
 * delay ZSet：看最早一条；到期 Lua 原子 ZREM + LPUSH ready。
 */
final class RedisDelayZSet {

    private static final DefaultRedisScript<Long> PROMOTE = new DefaultRedisScript<>();

    static {
        PROMOTE.setResultType(Long.class);
        PROMOTE.setScriptText("""
                local delay = KEYS[1]
                local ready = KEYS[2]
                local member = ARGV[1]
                local now = tonumber(ARGV[2])
                local score = redis.call('ZSCORE', delay, member)
                if not score then
                  return 0
                end
                if tonumber(score) > now then
                  return 0
                end
                redis.call('ZREM', delay, member)
                redis.call('LPUSH', ready, member)
                return 1
                """);
    }

    private final StringRedisTemplate redis;
    private final String delayKey;
    private final String readyKey;

    RedisDelayZSet(StringRedisTemplate redis, String delayKey, String readyKey) {
        this.redis = redis;
        this.delayKey = delayKey;
        this.readyKey = readyKey;
    }

    public OptionalLong peekScore() {
        Set<String> first = redis.opsForZSet().range(delayKey, 0, 0);
        if (first == null || first.isEmpty()) {
            return OptionalLong.empty();
        }
        String member = first.iterator().next();
        Double score = redis.opsForZSet().score(delayKey, member);
        if (score == null) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(score.longValue());
    }

    /**
     * @return 是否把最早一条推进了 ready
     */
    public boolean promoteFirstDue(long nowMillis) {
        Set<String> first = redis.opsForZSet().range(delayKey, 0, 0);
        if (first == null || first.isEmpty()) {
            return false;
        }
        String member = first.iterator().next();
        Long moved = redis.execute(PROMOTE, List.of(delayKey, readyKey), member, String.valueOf(nowMillis));
        return moved != null && moved > 0;
    }

    public void zadd(String member, long epochMs) {
        redis.opsForZSet().add(delayKey, member, epochMs);
    }

    public void remove(String member) {
        redis.opsForZSet().remove(delayKey, member);
    }

    public void publishWake(String wakeChannel) {
        redis.convertAndSend(wakeChannel, "1");
    }

    public static List<String> none() {
        return Collections.emptyList();
    }
}
