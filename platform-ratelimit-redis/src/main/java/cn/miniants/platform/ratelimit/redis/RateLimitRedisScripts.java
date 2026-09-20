package cn.miniants.platform.ratelimit.redis;

import cn.miniants.platform.ratelimit.RateLimitAlgorithm;
import cn.miniants.platform.ratelimit.RateLimitBackend;
import cn.miniants.platform.ratelimit.RateLimitDecision;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.RateLimitRequest;
import cn.miniants.platform.ratelimit.support.RateLimitSubjectKey;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 共享 Lua 脚本与参数/结果编解码，供同步与响应式限流器复用。
 *
 * <p>时间一律取 Redis {@code TIME}，避免多实例时钟偏差。
 * 脚本返回四元组：{@code allowed(0|1), remaining, retryAfterMs, resetAtMs}。
 */
final class RateLimitRedisScripts {

    /**
     * GCRA：键值为 TAT（毫秒浮点字符串）。
     * ARGV: limit, burst, periodMs, cost, ttlMs
     */
    @SuppressWarnings("rawtypes")
    static final RedisScript<List> GCRA = new DefaultRedisScript<>("""
            local time = redis.call('TIME')
            local nowMs = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            local limit = tonumber(ARGV[1])
            local burst = tonumber(ARGV[2])
            local periodMs = tonumber(ARGV[3])
            local cost = tonumber(ARGV[4])
            local ttlMs = tonumber(ARGV[5])
            local emissionInterval = periodMs / limit
            local tau = emissionInterval * burst
            local raw = redis.call('GET', KEYS[1])
            local tat = raw and tonumber(raw) or nowMs
            local newTat = math.max(nowMs, tat) + cost * emissionInterval
            local earliest = newTat - tau
            if nowMs < earliest then
              local retryMs = math.ceil(earliest - nowMs)
              if retryMs < 1 then
                retryMs = 1
              end
              local remainingTokens = math.max(0, (nowMs + tau - math.max(nowMs, tat)) / emissionInterval)
              local remaining = math.min(limit, math.floor(remainingTokens))
              if raw then
                redis.call('PEXPIRE', KEYS[1], ttlMs)
              end
              return {0, remaining, retryMs, nowMs + retryMs}
            end
            redis.call('SET', KEYS[1], tostring(newTat))
            redis.call('PEXPIRE', KEYS[1], ttlMs)
            local remainingTokens = math.max(0, (nowMs + tau - newTat) / emissionInterval)
            local remaining = math.min(limit, math.floor(remainingTokens))
            local resetMs = math.ceil(newTat)
            if resetMs < nowMs then
              resetMs = nowMs
            end
            return {1, remaining, 0, resetMs}
            """, List.class);

    /**
     * 滑动窗口：ZSET score=时间戳毫秒，每个 cost 单位一条 member。
     * ARGV: periodMs, limit, cost, ttlMs, memberPrefix
     */
    @SuppressWarnings("rawtypes")
    static final RedisScript<List> SLIDING_WINDOW = new DefaultRedisScript<>("""
            local time = redis.call('TIME')
            local nowMs = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            local periodMs = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            local cost = tonumber(ARGV[3])
            local ttlMs = tonumber(ARGV[4])
            local memberPrefix = ARGV[5]
            local cutoff = nowMs - periodMs
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', cutoff)
            local used = redis.call('ZCARD', KEYS[1])
            if used + cost > limit then
              local needFree = used + cost - limit
              local members = redis.call('ZRANGE', KEYS[1], 0, -1, 'WITHSCORES')
              local freed = 0
              local retryMs = 1
              for i = 1, #members, 2 do
                freed = freed + 1
                if freed >= needFree then
                  local expireAt = tonumber(members[i + 1]) + periodMs
                  retryMs = math.max(1, expireAt - nowMs)
                  break
                end
              end
              local remaining = math.max(0, limit - used)
              if used > 0 then
                redis.call('PEXPIRE', KEYS[1], ttlMs)
              end
              return {0, remaining, retryMs, nowMs + retryMs}
            end
            for i = 1, cost do
              redis.call('ZADD', KEYS[1], nowMs, memberPrefix .. ':' .. i)
            end
            redis.call('PEXPIRE', KEYS[1], ttlMs)
            local remaining = math.max(0, limit - (used + cost))
            local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
            local resetMs = nowMs
            if #oldest >= 2 then
              resetMs = tonumber(oldest[2]) + periodMs
              if resetMs < nowMs then
                resetMs = nowMs
              end
            end
            return {1, remaining, 0, resetMs}
            """, List.class);

    private RateLimitRedisScripts() {
    }

    static String counterKey(String keyPrefix, RateLimitRequest request, RateLimitPolicy policy) {
        String subject = RateLimitSubjectKey.sanitize(request.subject());
        return RateLimitRedisKeys.counter(keyPrefix, policy.policyCode(), subject);
    }

    static List<String> gcraArgs(RateLimitRequest request, RateLimitPolicy policy) {
        long periodMs = policy.period().toMillis();
        long ttlMs = RateLimitRedisKeys.ttlMillis(periodMs);
        List<String> args = new ArrayList<>(5);
        args.add(Long.toString(policy.limit()));
        args.add(Long.toString(policy.burst()));
        args.add(Long.toString(periodMs));
        args.add(Integer.toString(request.cost()));
        args.add(Long.toString(ttlMs));
        return args;
    }

    static List<String> slidingWindowArgs(RateLimitRequest request, RateLimitPolicy policy) {
        long periodMs = policy.period().toMillis();
        long ttlMs = RateLimitRedisKeys.ttlMillis(periodMs);
        List<String> args = new ArrayList<>(5);
        args.add(Long.toString(periodMs));
        args.add(Long.toString(policy.limit()));
        args.add(Integer.toString(request.cost()));
        args.add(Long.toString(ttlMs));
        args.add(UUID.randomUUID().toString());
        return args;
    }

    static void validate(RateLimitRequest request, RateLimitPolicy policy) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(policy, "policy");
        if (policy.limit() <= 0) {
            throw new IllegalArgumentException("限流阈值必须为正数");
        }
        if (policy.period() == null || policy.period().isZero() || policy.period().isNegative()) {
            throw new IllegalArgumentException("限流周期必须为正");
        }
        if (policy.period().toMillis() <= 0) {
            throw new IllegalArgumentException("限流周期过短，毫秒精度下无效");
        }
        if (policy.algorithm() == RateLimitAlgorithm.GCRA && policy.burst() <= 0) {
            throw new IllegalArgumentException("突发容量必须为正数");
        }
    }

    static RateLimitDecision disabledAllow(RateLimitPolicy policy) {
        Instant resetAt = Instant.now().plus(policy.period());
        return RateLimitDecision.allowed(
                policy.limit(),
                policy.limit(),
                resetAt,
                policy.policyCode(),
                policy.algorithm(),
                RateLimitBackend.REDIS);
    }

    static RateLimitDecision storeFailure(RateLimitPolicy policy) {
        return switch (policy.storeFailurePolicy()) {
            case ALLOW -> RateLimitDecision.storeErrorAllow(
                    policy.limit(),
                    policy.policyCode(),
                    policy.algorithm(),
                    RateLimitBackend.REDIS);
            case DENY -> RateLimitDecision.storeErrorDeny(
                    policy.limit(),
                    policy.policyCode(),
                    policy.algorithm(),
                    RateLimitBackend.REDIS);
        };
    }

    @SuppressWarnings("rawtypes")
    static RateLimitDecision toDecision(List raw, RateLimitPolicy policy) {
        if (raw == null || raw.size() < 4) {
            throw new IllegalStateException("限流脚本返回格式无效");
        }
        long allowedFlag = toLong(raw.get(0));
        long remaining = Math.max(0L, toLong(raw.get(1)));
        long retryMs = Math.max(0L, toLong(raw.get(2)));
        long resetAtMs = toLong(raw.get(3));
        Instant resetAt = Instant.ofEpochMilli(resetAtMs);
        if (allowedFlag == 1L) {
            return RateLimitDecision.allowed(
                    policy.limit(),
                    remaining,
                    resetAt,
                    policy.policyCode(),
                    policy.algorithm(),
                    RateLimitBackend.REDIS);
        }
        long effectiveRetry = Math.max(1L, retryMs);
        return RateLimitDecision.denied(
                policy.limit(),
                remaining,
                Duration.ofMillis(effectiveRetry),
                resetAt,
                policy.policyCode(),
                policy.algorithm(),
                RateLimitBackend.REDIS);
    }

    private static long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
