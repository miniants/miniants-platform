package cn.miniants.platform.ratelimit.redis;

import cn.miniants.platform.ratelimit.RateLimitAlgorithm;
import cn.miniants.platform.ratelimit.RateLimitDecision;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.RateLimitRequest;
import cn.miniants.platform.ratelimit.spi.ReactiveRateLimiter;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

/**
 * 基于 Redis Lua 的响应式限流器；禁止调用阻塞 Redis API。
 */
public class ReactiveRedisRateLimiter implements ReactiveRateLimiter {

    private final ReactiveStringRedisTemplate redis;
    private final String keyPrefix;

    public ReactiveRedisRateLimiter(ReactiveStringRedisTemplate redis, String keyPrefix) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix");
    }

    @Override
    public Mono<RateLimitDecision> acquire(RateLimitRequest request, RateLimitPolicy policy) {
        try {
            RateLimitRedisScripts.validate(request, policy);
        } catch (RuntimeException ex) {
            return Mono.error(ex);
        }
        if (!policy.enabled()) {
            return Mono.just(RateLimitRedisScripts.disabledAllow(policy));
        }

        RateLimitAlgorithm algorithm = policy.algorithm();
        if (algorithm == null) {
            return Mono.error(new IllegalArgumentException("限流算法不能为空"));
        }

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
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Mono<RateLimitDecision> execute(
            RedisScript<List> script, String key, List<String> args, RateLimitPolicy policy) {
        return redis.execute(script, List.of(key), args)
                .collectList()
                .map(elements -> unwrapScriptResult(elements))
                .map(raw -> RateLimitRedisScripts.toDecision(raw, policy))
                .onErrorResume(ex -> Mono.just(RateLimitRedisScripts.storeFailure(policy)));
    }

    /**
     * 同步 {@code execute} 返回整表；响应式执行器可能把表元素拆成 Flux 多项，或一次发出整表。
     */
    @SuppressWarnings("rawtypes")
    private static List unwrapScriptResult(List<?> elements) {
        if (elements == null || elements.isEmpty()) {
            throw new IllegalStateException("限流脚本返回为空");
        }
        Object first = elements.get(0);
        if (elements.size() == 1 && first instanceof List<?> nested) {
            return List.copyOf(nested);
        }
        return List.copyOf(elements);
    }
}
