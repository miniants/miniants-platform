package cn.miniants.platform.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 就绪 List BRPOP：关闭态 / Redis 瞬断降噪。可对多键阻塞（本实例 ready + unassigned）。
 */
final class RedisBlockingPoller {

    private static final Logger log = LoggerFactory.getLogger(RedisBlockingPoller.class);

    private static final long TRANSIENT_WARN_INTERVAL_MS = 60_000L;
    /** 与全仓 {@code spring.redis.timeout: 4000} 对齐；探测失败时按此封顶。 */
    private static final long DEFAULT_CLIENT_TIMEOUT_MS = 4000L;

    private final StringRedisTemplate stringRedisTemplate;
    private final QueueWorkerSupport lifecycle;
    private final String logLabel;
    private volatile long lastTransientPollWarnAt;

    RedisBlockingPoller(StringRedisTemplate stringRedisTemplate,
            QueueWorkerSupport lifecycle, String logLabel) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.lifecycle = lifecycle;
        this.logLabel = logLabel;
    }

    public String pollReady(String readyKey, long timeout, TimeUnit unit) {
        return pollReady(List.of(readyKey), timeout, unit);
    }

    public String pollReady(List<String> readyKeys, long timeout, TimeUnit unit) {
        if (lifecycle.shouldStopPolling() || readyKeys == null || readyKeys.isEmpty()) {
            return null;
        }
        try {
            int seconds = brpopSeconds(timeout, unit, clientCommandTimeoutMs());
            return stringRedisTemplate.execute((RedisCallback<String>) connection -> {
                RedisSerializer<String> serializer = stringRedisTemplate.getStringSerializer();
                byte[][] rawKeys = new byte[readyKeys.size()][];
                for (int i = 0; i < readyKeys.size(); i++) {
                    rawKeys[i] = serializer.serialize(readyKeys.get(i));
                }
                List<byte[]> popped = connection.listCommands().bRPop(seconds, rawKeys);
                if (popped == null || popped.size() < 2) {
                    return null;
                }
                return new String(popped.get(1), StandardCharsets.UTF_8);
            });
        } catch (Exception ex) {
            if (RedisDisconnectClassifier.isRedisFactoryStopping(ex)) {
                lifecycle.markStopPolling();
                log.debug("{} pollReady：Redis 正在关闭，工人将退出 ({})",
                        logLabel, RedisDisconnectClassifier.summarize(ex));
                return null;
            }
            if (RedisDisconnectClassifier.isIdlePollTimeout(ex)) {
                log.debug("{} pollReady 空等超时: {}",
                        logLabel, RedisDisconnectClassifier.summarize(ex));
                return null;
            }
            if (lifecycle.isShuttingDown()
                    || RedisDisconnectClassifier.isTransientRedisDisconnect(ex)) {
                log.debug("{} pollReady 可忽略异常: {}",
                        logLabel, RedisDisconnectClassifier.summarize(ex));
                if (!lifecycle.isShuttingDown()) {
                    maybeWarnTransient(ex);
                }
                return null;
            }
            log.warn("{} pollReady 异常", logLabel, ex);
            return null;
        }
    }

    /**
     * BRPOP 必须短于 Lettuce 命令超时，否则空队列会被客户端掐成 QueryTimeoutException。
     */
    static int brpopSeconds(long timeout, TimeUnit unit, long clientTimeoutMs) {
        long requested = Math.max(1L, unit.toSeconds(timeout));
        long clientMs = clientTimeoutMs > 0 ? clientTimeoutMs : DEFAULT_CLIENT_TIMEOUT_MS;
        long maxWait = Math.max(1L, (clientMs / 1000L) - 1L);
        return (int) Math.min(requested, maxWait);
    }

    private long clientCommandTimeoutMs() {
        try {
            Object factory = stringRedisTemplate.getConnectionFactory();
            if (factory instanceof LettuceConnectionFactory lettuce) {
                Duration timeout = lettuce.getClientConfiguration().getCommandTimeout();
                if (timeout != null && !timeout.isZero() && !timeout.isNegative()) {
                    return timeout.toMillis();
                }
            }
        } catch (RuntimeException ignored) {
            // 非 Lettuce 或尚未就绪时走兜底
        }
        return DEFAULT_CLIENT_TIMEOUT_MS;
    }

    private void maybeWarnTransient(Throwable ex) {
        long now = System.currentTimeMillis();
        if (now - lastTransientPollWarnAt < TRANSIENT_WARN_INTERVAL_MS) {
            return;
        }
        lastTransientPollWarnAt = now;
        log.warn("{} Redis 暂不可用，工人将自动重试: {}",
                logLabel, RedisDisconnectClassifier.summarize(ex));
    }
}
