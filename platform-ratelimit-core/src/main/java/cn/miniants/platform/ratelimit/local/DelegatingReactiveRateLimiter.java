package cn.miniants.platform.ratelimit.local;

import cn.miniants.platform.ratelimit.RateLimitDecision;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.RateLimitRequest;
import cn.miniants.platform.ratelimit.spi.RateLimiter;
import cn.miniants.platform.ratelimit.spi.ReactiveRateLimiter;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * 将同步 {@link RateLimiter} 包装为 {@link ReactiveRateLimiter}（本地/测试用）。
 */
public class DelegatingReactiveRateLimiter implements ReactiveRateLimiter {

    private final RateLimiter delegate;

    public DelegatingReactiveRateLimiter(RateLimiter delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public Mono<RateLimitDecision> acquire(RateLimitRequest request, RateLimitPolicy policy) {
        return Mono.fromCallable((Callable<RateLimitDecision>) () -> delegate.acquire(request, policy));
    }
}
