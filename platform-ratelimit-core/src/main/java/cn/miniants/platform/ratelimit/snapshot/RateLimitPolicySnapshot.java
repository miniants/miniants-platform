package cn.miniants.platform.ratelimit.snapshot;

import cn.miniants.platform.ratelimit.RateLimitPolicy;

import java.util.List;

/**
 * 数据面 Redis 快照：修订号 + 运行时策略。不含管理端 ID / 审计字段。
 */
public record RateLimitPolicySnapshot(long revision, List<RateLimitPolicy> policies) {

    public RateLimitPolicySnapshot {
        policies = policies == null ? List.of() : List.copyOf(policies);
    }
}
