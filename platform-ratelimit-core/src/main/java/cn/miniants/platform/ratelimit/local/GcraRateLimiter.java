package cn.miniants.platform.ratelimit.local;

import cn.miniants.platform.ratelimit.RateLimitAlgorithm;
import cn.miniants.platform.ratelimit.RateLimitBackend;
import cn.miniants.platform.ratelimit.RateLimitDecision;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.RateLimitRequest;
import cn.miniants.platform.ratelimit.spi.RateLimitBucketView;
import cn.miniants.platform.ratelimit.spi.RateLimitClock;
import cn.miniants.platform.ratelimit.spi.RateLimiter;
import cn.miniants.platform.ratelimit.spi.SystemRateLimitClock;
import cn.miniants.platform.ratelimit.support.RateLimitBucketMath;
import cn.miniants.platform.ratelimit.support.RateLimitSubjectKey;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 本地 GCRA（Generic Cell Rate Algorithm）实现。
 *
 * <p>发射间隔 {@code T = period / limit}；延迟容差 {@code τ = burst * T}。
 * 与后续 Redis Lua 使用同一语义，便于双后端结果对齐。
 */
public class GcraRateLimiter implements RateLimiter {

    private static final int CLEANUP_EVERY = 64;

    private final RateLimitClock clock;
    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();
    private final AtomicLong acquireCount = new AtomicLong();

    public GcraRateLimiter() {
        this(SystemRateLimitClock.INSTANCE);
    }

    public GcraRateLimiter(RateLimitClock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public RateLimitDecision acquire(RateLimitRequest request, RateLimitPolicy policy) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(policy, "policy");
        validatePolicy(policy);

        if (!policy.enabled()) {
            Instant now = clock.now();
            return RateLimitDecision.allowed(
                    policy.limit(),
                    policy.limit(),
                    now.plus(policy.period()),
                    policy.policyCode(),
                    RateLimitAlgorithm.GCRA,
                    RateLimitBackend.LOCAL);
        }

        long nowMs = clock.currentTimeMillis();
        maybeCleanup(nowMs, policy.period().toMillis());

        String key = storageKey(request, policy);
        int cost = request.cost();
        long limit = policy.limit();
        long burst = policy.burst();
        long periodMs = policy.period().toMillis();
        if (periodMs <= 0) {
            throw new IllegalArgumentException("限流周期过短，毫秒精度下无效");
        }

        double emissionInterval = (double) periodMs / (double) limit;
        double tau = emissionInterval * (double) burst;

        AtomicReference<RateLimitDecision> decision = new AtomicReference<>();
        states.compute(key, (ignored, previous) -> {
            State state = previous == null ? new State(nowMs) : previous;
            double tat = state.tatMillis;
            double newTat = Math.max((double) nowMs, tat) + cost * emissionInterval;
            double earliest = newTat - tau;

            if (nowMs < earliest) {
                long retryMs = (long) Math.ceil(earliest - nowMs);
                if (retryMs < 1L) {
                    retryMs = 1L;
                }
                double remainingTokens =
                        Math.max(0.0, (nowMs + tau - Math.max((double) nowMs, tat)) / emissionInterval);
                long remaining = Math.min(limit, (long) Math.floor(remainingTokens));
                state.lastAccessMillis = nowMs;
                decision.set(RateLimitDecision.denied(
                        limit,
                        remaining,
                        Duration.ofMillis(retryMs),
                        Instant.ofEpochMilli(nowMs + retryMs),
                        policy.policyCode(),
                        RateLimitAlgorithm.GCRA,
                        RateLimitBackend.LOCAL));
                return state;
            }

            state.tatMillis = newTat;
            state.lastAccessMillis = nowMs;
            double remainingTokens = Math.max(0.0, (nowMs + tau - newTat) / emissionInterval);
            long remaining = Math.min(limit, (long) Math.floor(remainingTokens));
            long resetMs = (long) Math.ceil(newTat);
            decision.set(RateLimitDecision.allowed(
                    limit,
                    remaining,
                    Instant.ofEpochMilli(Math.max(resetMs, nowMs)),
                    policy.policyCode(),
                    RateLimitAlgorithm.GCRA,
                    RateLimitBackend.LOCAL));
            return state;
        });

        RateLimitDecision result = decision.get();
        if (result == null) {
            throw new IllegalStateException("限流决策未生成");
        }
        return result;
    }

    /** 测试与运维用：当前内存键数量。 */
    public int size() {
        return states.size();
    }

    public List<RateLimitBucketView> inspect(Map<String, RateLimitPolicy> policies, long nowMs, int max) {
        List<RateLimitBucketView> rows = new ArrayList<>();
        if (max <= 0) {
            return rows;
        }
        for (Map.Entry<String, State> entry : states.entrySet()) {
            if (rows.size() >= max) {
                break;
            }
            ParsedStorageKey parsed = parseStorageKey(entry.getKey());
            if (parsed == null) {
                continue;
            }
            RateLimitPolicy policy = policies == null ? null : policies.get(parsed.policyCode());
            if (policy == null || policy.algorithm() != RateLimitAlgorithm.GCRA) {
                continue;
            }
            RateLimitBucketMath.Peek peek = RateLimitBucketMath.peekGcra(
                    nowMs, policy.limit(), policy.burst(), policy.period().toMillis(), entry.getValue().tatMillis);
            rows.add(new RateLimitBucketView(
                    parsed.policyCode(),
                    parsed.subject(),
                    RateLimitAlgorithm.GCRA.name(),
                    policy.limit(),
                    peek.remaining(),
                    peek.retryAfterMs(),
                    peek.resetAtMs()));
        }
        return rows;
    }

    private static void validatePolicy(RateLimitPolicy policy) {
        if (policy.algorithm() != RateLimitAlgorithm.GCRA) {
            throw new IllegalArgumentException("GcraRateLimiter 仅支持 GCRA 算法");
        }
        if (policy.limit() <= 0) {
            throw new IllegalArgumentException("限流阈值必须为正数");
        }
        if (policy.burst() <= 0) {
            throw new IllegalArgumentException("突发容量必须为正数");
        }
        if (policy.period() == null || policy.period().isZero() || policy.period().isNegative()) {
            throw new IllegalArgumentException("限流周期必须为正");
        }
    }

    private static String storageKey(RateLimitRequest request, RateLimitPolicy policy) {
        return policy.policyCode() + '\0' + RateLimitSubjectKey.sanitize(request.subject());
    }

    private static ParsedStorageKey parseStorageKey(String storageKey) {
        int split = storageKey.indexOf('\0');
        if (split <= 0 || split >= storageKey.length() - 1) {
            return null;
        }
        return new ParsedStorageKey(storageKey.substring(0, split), storageKey.substring(split + 1));
    }

    private record ParsedStorageKey(String policyCode, String subject) {
    }

    private void maybeCleanup(long nowMs, long periodMs) {
        if (acquireCount.incrementAndGet() % CLEANUP_EVERY != 0) {
            return;
        }
        long ttl = Math.max(periodMs * 2L, periodMs + 1_000L);
        Iterator<Map.Entry<String, State>> it = states.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, State> entry = it.next();
            State state = entry.getValue();
            long idleBasis = Math.max(state.lastAccessMillis, (long) Math.ceil(state.tatMillis));
            if (nowMs - idleBasis > ttl) {
                it.remove();
            }
        }
    }

    private static final class State {
        private double tatMillis;
        private long lastAccessMillis;

        private State(long nowMs) {
            this.tatMillis = nowMs;
            this.lastAccessMillis = nowMs;
        }
    }
}
