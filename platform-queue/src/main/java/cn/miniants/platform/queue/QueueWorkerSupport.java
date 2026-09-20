package cn.miniants.platform.queue;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 队列关闭 / 停领状态：供 ContinuousQueue 与 Worker drain 共用。
 */
final class QueueWorkerSupport {

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicBoolean stopPolling = new AtomicBoolean(false);

    public void beginShutdown() {
        shuttingDown.set(true);
        stopPolling.set(true);
    }

    public boolean isShuttingDown() {
        return shuttingDown.get();
    }

    public boolean shouldStopPolling() {
        return shuttingDown.get() || stopPolling.get();
    }

    public void markStopPolling() {
        stopPolling.set(true);
    }
}
