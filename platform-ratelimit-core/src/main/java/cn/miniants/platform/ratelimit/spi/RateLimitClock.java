package cn.miniants.platform.ratelimit.spi;

import java.time.Instant;

/**
 * 可注入的时钟，便于确定性测试。
 */
public interface RateLimitClock {

    Instant now();

    long currentTimeMillis();
}
