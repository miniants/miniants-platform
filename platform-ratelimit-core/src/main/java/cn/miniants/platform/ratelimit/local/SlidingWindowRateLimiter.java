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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 本地严格滑动窗口计数器：任意连续 {@code period} 内不超过 {@code limit} 次（含 cost）。
 */
public class SlidingWindowRateLimiter implements RateLimiter {

    private static final int CLEANUP_EVERY = 64;

    private final RateLimitClock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong acquireCount = new AtomicLong();

    public SlidingWindowRateLimiter() {
        this(SystemRateLimitClock.INSTANCE);
    }

    public SlidingWindowRateLimiter(RateLimitClock clock) {
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
                    RateLimitAlgorithm.SLIDING_WINDOW,
                    RateLimitBackend.LOCAL);
        }

        long nowMs = clock.currentTimeMillis();
        long windowMs = policy.period().toMillis();
        if (windowMs <= 0) {
            throw new IllegalArgumentException("限流周期过短，毫秒精度下无效");
        }
        maybeCleanup(nowMs, windowMs);

        String key = storageKey(request, policy);
        int cost = request.cost();
        long limit = policy.limit();

        Window window = windows.computeIfAbsent(key, ignored -> new Window());
        synchronized (window) {
            long cutoff = nowMs - windowMs;
            evictExpired(window, cutoff);

            long used = window.totalCost;
            if (used + cost > limit) {
                long retryMs = retryAfterMillis(window, nowMs, windowMs, limit, cost);
                Duration retryAfter = Duration.ofMillis(Math.max(retryMs, 1L));
                Instant resetAt = Instant.ofEpochMilli(nowMs + Math.max(retryMs, 1L));
                long remaining = Math.max(0L, limit - used);
                return RateLimitDecision.denied(
                        limit,
                        remaining,
                        retryAfter,
                        resetAt,
                        policy.policyCode(),
                        RateLimitAlgorithm.SLIDING_WINDOW,
                        RateLimitBackend.LOCAL);
            }

            window.hits.addLast(new Hit(nowMs, cost));
            window.totalCost += cost;
            window.lastAccessMillis = nowMs;

            long remaining = Math.max(0L, limit - window.totalCost);
            long resetMs = window.hits.isEmpty()
                    ? nowMs
                    : window.hits.peekFirst().atMillis + windowMs;
            Instant resetAt = Instant.ofEpochMilli(Math.max(resetMs, nowMs));
            return RateLimitDecision.allowed(
                    limit,
                    remaining,
                    resetAt,
                    policy.policyCode(),
                    RateLimitAlgorithm.SLIDING_WINDOW,
                    RateLimitBackend.LOCAL);
        }
    }

    public int size() {
        return windows.size();
    }

    public List<RateLimitBucketView> inspect(Map<String, RateLimitPolicy> policies, long nowMs, int max) {
        List<RateLimitBucketView> rows = new ArrayList<>();
        if (max <= 0) {
            return rows;
        }
        for (Map.Entry<String, Window> entry : windows.entrySet()) {
            if (rows.size() >= max) {
                break;
            }
            ParsedStorageKey parsed = parseStorageKey(entry.getKey());
            if (parsed == null) {
                continue;
            }
            RateLimitPolicy policy = policies == null ? null : policies.get(parsed.policyCode());
            if (policy == null || policy.algorithm() != RateLimitAlgorithm.SLIDING_WINDOW) {
                continue;
            }
            long periodMs = policy.period().toMillis();
            Window window = entry.getValue();
            long used;
            Long oldestAt = null;
            synchronized (window) {
                evictExpired(window, nowMs - periodMs);
                used = window.totalCost;
                if (!window.hits.isEmpty()) {
                    oldestAt = window.hits.peekFirst().atMillis;
                }
            }
            RateLimitBucketMath.Peek peek = RateLimitBucketMath.peekSlidingWindow(
                    nowMs, policy.limit(), periodMs, used, oldestAt);
            rows.add(new RateLimitBucketView(
                    parsed.policyCode(),
                    parsed.subject(),
                    RateLimitAlgorithm.SLIDING_WINDOW.name(),
                    policy.limit(),
                    peek.remaining(),
                    peek.retryAfterMs(),
                    peek.resetAtMs()));
        }
        return rows;
    }

    private static void validatePolicy(RateLimitPolicy policy) {
        if (policy.algorithm() != RateLimitAlgorithm.SLIDING_WINDOW) {
            throw new IllegalArgumentException("SlidingWindowRateLimiter 仅支持 SLIDING_WINDOW 算法");
        }
        if (policy.limit() <= 0) {
            throw new IllegalArgumentException("限流阈值必须为正数");
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

    private static void evictExpired(Window window, long cutoff) {
        while (!window.hits.isEmpty() && window.hits.peekFirst().atMillis <= cutoff) {
            Hit removed = window.hits.pollFirst();
            window.totalCost -= removed.cost;
        }
        if (window.totalCost < 0) {
            window.totalCost = 0;
        }
    }

    /**
     * 等到最旧若干命中过期后，窗口内用量 + cost 不超过 limit。
     */
    private static long retryAfterMillis(Window window, long nowMs, long windowMs, long limit, int cost) {
        if (window.hits.isEmpty()) {
            return 1L;
        }
        long needFree = window.totalCost + cost - limit;
        if (needFree <= 0) {
            return 1L;
        }
        long freed = 0;
        for (Hit hit : window.hits) {
            freed += hit.cost;
            if (freed >= needFree) {
                long expireAt = hit.atMillis + windowMs;
                return Math.max(1L, expireAt - nowMs);
            }
        }
        Hit oldest = window.hits.peekFirst();
        return Math.max(1L, oldest.atMillis + windowMs - nowMs);
    }

    private void maybeCleanup(long nowMs, long windowMs) {
        if (acquireCount.incrementAndGet() % CLEANUP_EVERY != 0) {
            return;
        }
        long ttl = windowMs * 2L;
        Iterator<Map.Entry<String, Window>> it = windows.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Window> entry = it.next();
            Window window = entry.getValue();
            synchronized (window) {
                evictExpired(window, nowMs - windowMs);
                if (window.hits.isEmpty() && nowMs - window.lastAccessMillis > ttl) {
                    it.remove();
                }
            }
        }
    }

    private static final class Window {
        private final Deque<Hit> hits = new ArrayDeque<>();
        private long totalCost;
        private long lastAccessMillis;
    }

    private record Hit(long atMillis, int cost) {
    }
}
