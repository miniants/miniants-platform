package cn.miniants.platform.ratelimit;

/**
 * 存储（如 Redis）不可用时的失败策略。
 */
public enum StoreFailurePolicy {

    /** 放行请求。 */
    ALLOW,

    /** 拒绝请求。 */
    DENY
}
