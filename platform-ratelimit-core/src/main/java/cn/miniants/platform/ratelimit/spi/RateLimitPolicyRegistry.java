package cn.miniants.platform.ratelimit.spi;

import cn.miniants.platform.ratelimit.RateLimitPolicy;

import java.util.Optional;

/**
 * 从本地策略快照按编码解析策略。
 */
public interface RateLimitPolicyRegistry {

    Optional<RateLimitPolicy> find(String policyCode);

    default RateLimitPolicy require(String policyCode) {
        return find(policyCode)
                .orElseThrow(() -> new IllegalArgumentException("未找到限流策略: " + policyCode));
    }
}
