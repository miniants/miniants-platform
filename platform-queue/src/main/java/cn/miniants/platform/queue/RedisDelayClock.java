package cn.miniants.platform.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 闹钟线程：看 delay 最早一条，未到期 park，到期 Lua 推进 ready；wake 频道叫醒重算。
 * 每个跑工人的实例一条线程，不是整个 JVM 睡死。
 */
final class RedisDelayClock implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisDelayClock.class);

    private static final long MAX_PARK_MS = 60_000L;

    private final String logLabel;
    private final QueueBackend backend;
    private final QueueWorkerSupport lifecycle;
    private final RedisMessageListenerContainer listenerContainer;
    private final String wakeChannel;
    private final Object parkLock = new Object();
    private volatile boolean wakeRequested;
    private Thread thread;

    RedisDelayClock(String logLabel, QueueBackend backend, QueueWorkerSupport lifecycle,
            RedisMessageListenerContainer listenerContainer, String wakeChannel) {
        this.logLabel = logLabel;
        this.backend = backend;
        this.lifecycle = lifecycle;
        this.listenerContainer = listenerContainer;
        this.wakeChannel = wakeChannel;
    }

    public void start() {
        if (listenerContainer != null && wakeChannel != null) {
            listenerContainer.addMessageListener(this, new ChannelTopic(wakeChannel));
        }
        thread = new Thread(this::run, logLabel + "-delay-clock");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        if (listenerContainer != null && wakeChannel != null) {
            try {
                listenerContainer.removeMessageListener(this);
            } catch (RuntimeException ignored) {
                // 关闭过程容器可能已停
            }
        }
        wake();
        if (thread != null) {
            thread.interrupt();
        }
    }

    public void wake() {
        synchronized (parkLock) {
            wakeRequested = true;
            parkLock.notifyAll();
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        wake();
    }

    private void run() {
        log.info("{} 闹钟线程启动", logLabel);
        while (!lifecycle.shouldStopPolling()) {
            try {
                tick();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                if (lifecycle.shouldStopPolling() || RedisDisconnectClassifier.isRedisFactoryStopping(ex)) {
                    break;
                }
                log.warn("{} 闹钟异常，稍后重试", logLabel, ex);
                park(5_000L);
            }
        }
        log.info("{} 闹钟线程退出", logLabel);
    }

    private void tick() throws InterruptedException {
        if (lifecycle.shouldStopPolling()) {
            return;
        }
        java.util.OptionalLong score = backend.peekDelayScore();
        if (score.isEmpty()) {
            park(MAX_PARK_MS);
            return;
        }
        long now = System.currentTimeMillis();
        if (score.getAsLong() <= now) {
            backend.promoteFirstDue();
            return;
        }
        long wait = Math.min(score.getAsLong() - now, MAX_PARK_MS);
        park(wait);
    }

    private void park(long ms) {
        if (ms <= 0) {
            return;
        }
        synchronized (parkLock) {
            if (wakeRequested) {
                wakeRequested = false;
                return;
            }
            try {
                parkLock.wait(ms);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            wakeRequested = false;
        }
    }
}
