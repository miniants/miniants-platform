package cn.miniants.platform.ratelimit.spi;

import cn.miniants.platform.ratelimit.RateLimitPolicy;

import java.util.Collection;
import java.util.List;

/**
 * 代码内置策略贡献点。优先级低于 YAML 与动态快照。
 */
@FunctionalInterface
public interface RateLimitPolicyContributor {

    Collection<RateLimitPolicy> contribute();

    static RateLimitPolicyContributor of(RateLimitPolicy... policies) {
        return () -> List.of(policies);
    }
}
