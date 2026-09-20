package cn.miniants.platform.ratelimit;

import cn.miniants.platform.ratelimit.spi.RateLimitClock;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 可手动拨快的测试时钟。
 */
public final class MutableRateLimitClock implements RateLimitClock {

    private final AtomicLong millis;

    public MutableRateLimitClock(long initialMillis) {
        this.millis = new AtomicLong(initialMillis);
    }

    public void advanceMillis(long delta) {
        millis.addAndGet(delta);
    }

    public void setMillis(long value) {
        millis.set(value);
    }

    @Override
    public Instant now() {
        return Instant.ofEpochMilli(millis.get());
    }

    @Override
    public long currentTimeMillis() {
        return millis.get();
    }
}
