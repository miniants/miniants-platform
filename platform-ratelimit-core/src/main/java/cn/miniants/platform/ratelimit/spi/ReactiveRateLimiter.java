package cn.miniants.platform.ratelimit.spi;

import cn.miniants.platform.ratelimit.RateLimitDecision;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.RateLimitRequest;
import reactor.core.publisher.Mono;

/**
 * 响应式限流器。实现方应避免在 Reactor 线程上阻塞；本地实现可包装同步限流器。
 */
public interface ReactiveRateLimiter {

    Mono<RateLimitDecision> acquire(RateLimitRequest request, RateLimitPolicy policy);
}
