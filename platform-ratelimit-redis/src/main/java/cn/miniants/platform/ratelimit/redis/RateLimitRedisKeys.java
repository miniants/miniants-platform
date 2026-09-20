package cn.miniants.platform.ratelimit.redis;

import java.util.Objects;

/**
 * Redis 限流键空间约定。
 *
 * <p>计数键：{@code {keyPrefix}c:{policyCode}:{subject}}，主体为消毒后的明文。
 * 解析时只在 {@code c:} 之后的第一个 {@code :} 切开策略与主体（IPv6 可含冒号）。
 */
public final class RateLimitRedisKeys {

    public static final String COUNTER_SEGMENT = "c:";
    public static final String POLICY_SNAPSHOT_SUFFIX = "policy:snapshot";
    public static final String POLICY_REVISION_SUFFIX = "policy:revision";
    public static final String POLICY_EVENTS_SUFFIX = "policy:events";

    /** 周期之外的键存活宽限（毫秒）。 */
    public static final long TTL_GRACE_MILLIS = 1_000L;

    private RateLimitRedisKeys() {
    }

    public static String counter(String keyPrefix, String policyCode, String subject) {
        Objects.requireNonNull(keyPrefix, "keyPrefix");
        Objects.requireNonNull(policyCode, "policyCode");
        Objects.requireNonNull(subject, "subject");
        return keyPrefix + COUNTER_SEGMENT + policyCode + ':' + subject;
    }

    public static String counterScanPattern(String keyPrefix) {
        Objects.requireNonNull(keyPrefix, "keyPrefix");
        return keyPrefix + COUNTER_SEGMENT + "*";
    }

    /**
     * 从完整 Redis 键解析策略与主体。{@code c:} 之后只切第一个冒号。
     */
    public static CounterKey parseCounter(String keyPrefix, String redisKey) {
        Objects.requireNonNull(keyPrefix, "keyPrefix");
        if (redisKey == null || !redisKey.startsWith(keyPrefix + COUNTER_SEGMENT)) {
            return null;
        }
        String rest = redisKey.substring(keyPrefix.length() + COUNTER_SEGMENT.length());
        int colon = rest.indexOf(':');
        if (colon <= 0 || colon >= rest.length() - 1) {
            return null;
        }
        String policyCode = rest.substring(0, colon);
        String subject = rest.substring(colon + 1);
        if (policyCode.isBlank() || subject.isBlank()) {
            return null;
        }
        return new CounterKey(policyCode, subject);
    }

    public record CounterKey(String policyCode, String subject) {
    }

    public static String policySnapshot(String keyPrefix) {
        Objects.requireNonNull(keyPrefix, "keyPrefix");
        return keyPrefix + POLICY_SNAPSHOT_SUFFIX;
    }

    public static String policyRevision(String keyPrefix) {
        Objects.requireNonNull(keyPrefix, "keyPrefix");
        return keyPrefix + POLICY_REVISION_SUFFIX;
    }

    public static String policyEventsChannel(String keyPrefix) {
        Objects.requireNonNull(keyPrefix, "keyPrefix");
        return keyPrefix + POLICY_EVENTS_SUFFIX;
    }

    public static long ttlMillis(long periodMillis) {
        if (periodMillis <= 0L) {
            throw new IllegalArgumentException("限流周期过短，毫秒精度下无效");
        }
        long ttl = periodMillis + TTL_GRACE_MILLIS;
        if (ttl < periodMillis) {
            return Long.MAX_VALUE;
        }
        return ttl;
    }
}
