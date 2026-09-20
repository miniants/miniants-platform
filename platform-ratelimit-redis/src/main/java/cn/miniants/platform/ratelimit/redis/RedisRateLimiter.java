package cn.miniants.platform.ratelimit.redis;

import cn.miniants.platform.ratelimit.RateLimitAlgorithm;
import cn.miniants.platform.ratelimit.RateLimitDecision;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.RateLimitRequest;
import cn.miniants.platform.ratelimit.spi.RateLimiter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.Objects;

/**
 * 基于 Redis Lua 的同步限流器（GCRA / 滑动窗口）。
 */
public class RedisRateLimiter implements RateLimiter {

    private final StringRedisTemplate redis;
    private final String keyPrefix;

    public RedisRateLimiter(StringRedisTemplate redis, String keyPrefix) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix");
    }

    @Override
    public RateLimitDecision acquire(RateLimitRequest request, RateLimitPolicy policy) {
        RateLimitRedisScripts.validate(request, policy);
        if (!policy.enabled()) {
            return RateLimitRedisScripts.disabledAllow(policy);
        }

        RateLimitAlgorithm algorithm = policy.algorithm();
        if (algorithm == null) {
            throw new IllegalArgumentException("限流算法不能为空");
        }

        try {
            return switch (algorithm) {
                case GCRA -> execute(
                        RateLimitRedisScripts.GCRA,
                        RateLimitRedisScripts.counterKey(keyPrefix, request, policy),
                        RateLimitRedisScripts.gcraArgs(request, policy),
                        policy);
                case SLIDING_WINDOW -> execute(
                        RateLimitRedisScripts.SLIDING_WINDOW,
                        RateLimitRedisScripts.counterKey(keyPrefix, request, policy),
                        RateLimitRedisScripts.slidingWindowArgs(request, policy),
                        policy);
            };
        } catch (RuntimeException ex) {
            return RateLimitRedisScripts.storeFailure(policy);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private RateLimitDecision execute(
            RedisScript<List> script, String key, List<String> args, RateLimitPolicy policy) {
        List raw = redis.execute(script, List.of(key), args.toArray());
        return RateLimitRedisScripts.toDecision(raw, policy);
    }
}
