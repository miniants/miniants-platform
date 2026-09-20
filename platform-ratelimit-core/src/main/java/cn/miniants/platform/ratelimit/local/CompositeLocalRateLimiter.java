package cn.miniants.platform.ratelimit.local;

import cn.miniants.platform.ratelimit.RateLimitAlgorithm;
import cn.miniants.platform.ratelimit.RateLimitDecision;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.RateLimitRequest;
import cn.miniants.platform.ratelimit.spi.RateLimitBucketView;
import cn.miniants.platform.ratelimit.spi.RateLimitClock;
import cn.miniants.platform.ratelimit.spi.RateLimiter;
import cn.miniants.platform.ratelimit.spi.SystemRateLimitClock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 按策略算法分发到本地 GCRA 或滑动窗口实现。
 */
public class CompositeLocalRateLimiter implements RateLimiter {

    private final GcraRateLimiter gcra;
    private final SlidingWindowRateLimiter slidingWindow;

    public CompositeLocalRateLimiter() {
        this(SystemRateLimitClock.INSTANCE);
    }

    public CompositeLocalRateLimiter(RateLimitClock clock) {
        Objects.requireNonNull(clock, "clock");
        this.gcra = new GcraRateLimiter(clock);
        this.slidingWindow = new SlidingWindowRateLimiter(clock);
    }

    public CompositeLocalRateLimiter(GcraRateLimiter gcra, SlidingWindowRateLimiter slidingWindow) {
        this.gcra = Objects.requireNonNull(gcra, "gcra");
        this.slidingWindow = Objects.requireNonNull(slidingWindow, "slidingWindow");
    }

    @Override
    public RateLimitDecision acquire(RateLimitRequest request, RateLimitPolicy policy) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(policy, "policy");
        RateLimitAlgorithm algorithm = policy.algorithm();
        if (algorithm == null) {
            throw new IllegalArgumentException("限流算法不能为空");
        }
        return switch (algorithm) {
            case GCRA -> gcra.acquire(request, policy);
            case SLIDING_WINDOW -> slidingWindow.acquire(request, policy);
        };
    }

    public List<RateLimitBucketView> inspect(Map<String, RateLimitPolicy> policies, long nowMs, int max) {
        if (max <= 0) {
            return List.of();
        }
        List<RateLimitBucketView> rows = new ArrayList<>(gcra.inspect(policies, nowMs, max));
        if (rows.size() < max) {
            rows.addAll(slidingWindow.inspect(policies, nowMs, max - rows.size()));
        }
        return rows;
    }

    public int size() {
        return gcra.size() + slidingWindow.size();
    }
}
