package cn.miniants.platform.ratelimit.support;

/**
 * 只读换算：把 GCRA TAT / 滑动窗口用量变成 remaining 与 retryAfter，不消耗额度。
 */
public final class RateLimitBucketMath {

    private RateLimitBucketMath() {
    }

    public record Peek(long remaining, long retryAfterMs, long resetAtMs) {
    }

    public static Peek peekGcra(long nowMs, long limit, long burst, long periodMs, double tat) {
        if (limit <= 0 || burst <= 0 || periodMs <= 0) {
            return new Peek(0L, 0L, nowMs);
        }
        double emissionInterval = (double) periodMs / (double) limit;
        double tau = emissionInterval * (double) burst;
        double remainingTokens = Math.max(0.0, (nowMs + tau - Math.max((double) nowMs, tat)) / emissionInterval);
        long remaining = Math.min(limit, (long) Math.floor(remainingTokens));
        double earliest = Math.max((double) nowMs, tat) + emissionInterval - tau;
        long retryMs = 0L;
        if (nowMs < earliest) {
            retryMs = Math.max(1L, (long) Math.ceil(earliest - nowMs));
        }
        long resetMs = Math.max(nowMs, (long) Math.ceil(tat));
        return new Peek(remaining, retryMs, resetMs);
    }

    public static Peek peekSlidingWindow(long nowMs, long limit, long periodMs, long used, Long oldestAtMs) {
        long remaining = Math.max(0L, limit - used);
        if (used <= 0L || oldestAtMs == null) {
            return new Peek(Math.max(0L, limit), 0L, nowMs);
        }
        long resetMs = Math.max(nowMs, oldestAtMs + periodMs);
        long retryMs = 0L;
        if (remaining <= 0L) {
            retryMs = Math.max(1L, resetMs - nowMs);
        }
        return new Peek(remaining, retryMs, resetMs);
    }
}
