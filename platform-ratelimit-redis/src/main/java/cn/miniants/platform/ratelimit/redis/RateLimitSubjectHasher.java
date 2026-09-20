package cn.miniants.platform.ratelimit.redis;

/**
 * Redis 键用主体哈希，实现委托给 core。
 */
public final class RateLimitSubjectHasher {

    private RateLimitSubjectHasher() {
    }

    public static String hash(String subject) {
        return cn.miniants.platform.ratelimit.support.RateLimitSubjectHasher.hash(subject);
    }
}
