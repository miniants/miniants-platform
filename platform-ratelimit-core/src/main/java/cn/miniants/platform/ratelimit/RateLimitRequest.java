package cn.miniants.platform.ratelimit;

import java.util.Objects;

/**
 * 一次限流申请。
 *
 * @param policy  策略编码（与 {@link RateLimitPolicy#policyCode()} 对应）
 * @param subject 主体键（如 IP、用户 ID）
 * @param cost    本次消耗额度，默认 1
 */
public record RateLimitRequest(String policy, String subject, int cost) {

    public RateLimitRequest {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(subject, "subject");
        if (policy.isBlank()) {
            throw new IllegalArgumentException("策略编码不能为空");
        }
        if (subject.isBlank()) {
            throw new IllegalArgumentException("限流主体不能为空");
        }
        if (cost <= 0) {
            throw new IllegalArgumentException("消耗额度必须为正数");
        }
    }

    public RateLimitRequest(String policy, String subject) {
        this(policy, subject, 1);
    }

    public static RateLimitRequest of(String policy, String subject) {
        return new RateLimitRequest(policy, subject, 1);
    }

    public static RateLimitRequest of(String policy, String subject, int cost) {
        return new RateLimitRequest(policy, subject, cost);
    }
}
