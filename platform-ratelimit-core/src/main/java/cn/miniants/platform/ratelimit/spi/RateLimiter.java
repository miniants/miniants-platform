package cn.miniants.platform.ratelimit.spi;

import cn.miniants.platform.ratelimit.RateLimitDecision;
import cn.miniants.platform.ratelimit.RateLimitExceededException;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.RateLimitRequest;

/**
 * 同步限流器。
 */
public interface RateLimiter {

    RateLimitDecision acquire(RateLimitRequest request, RateLimitPolicy policy);

    /**
     * 申请额度；拒绝时抛出 {@link RateLimitExceededException}。
     */
    default RateLimitDecision require(RateLimitRequest request, RateLimitPolicy policy) {
        RateLimitDecision decision = acquire(request, policy);
        if (!decision.allowed()) {
            throw new RateLimitExceededException(decision);
        }
        return decision;
    }
}
