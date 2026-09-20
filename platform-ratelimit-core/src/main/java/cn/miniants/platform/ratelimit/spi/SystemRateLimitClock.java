package cn.miniants.platform.ratelimit.spi;

import java.time.Instant;

/**
 * 系统时钟。
 */
public final class SystemRateLimitClock implements RateLimitClock {

    public static final SystemRateLimitClock INSTANCE = new SystemRateLimitClock();

    @Override
    public Instant now() {
        return Instant.now();
    }

    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
