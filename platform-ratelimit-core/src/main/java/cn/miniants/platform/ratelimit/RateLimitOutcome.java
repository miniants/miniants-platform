package cn.miniants.platform.ratelimit;

/**
 * 限流决策结果分类。
 */
public enum RateLimitOutcome {

    ALLOWED,
    DENIED,
    STORE_ERROR_ALLOW,
    STORE_ERROR_DENY,
    STALE_POLICY_ALLOW,
    STALE_POLICY_DENY
}
