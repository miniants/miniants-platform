package cn.miniants.platform.ratelimit.spi;

/**
 * 只读列出当前后端的限流桶。管理端实时数据用；不要走 {@code KEYS}。
 */
public interface RateLimitBucketInspector {

    RateLimitBucketSnapshot inspect(int maxBuckets);
}
