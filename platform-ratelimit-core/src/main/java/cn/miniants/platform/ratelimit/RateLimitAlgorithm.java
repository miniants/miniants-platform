package cn.miniants.platform.ratelimit;

/**
 * 限流算法。
 */
public enum RateLimitAlgorithm {

    /** GCRA / 令牌桶：平滑速率，支持 burst。 */
    GCRA,

    /** 严格滑动窗口：周期内恰好 N 次。 */
    SLIDING_WINDOW
}
