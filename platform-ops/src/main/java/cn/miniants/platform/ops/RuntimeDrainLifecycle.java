package cn.miniants.platform.ops;

import org.springframework.context.SmartLifecycle;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 容器停机时先翻排空，让工人停接新任务。HTTP 优雅停机仍走 Boot {@code server.shutdown}。
 */
public class RuntimeDrainLifecycle implements SmartLifecycle {

    private final RuntimeDrain drain;
    private final AtomicBoolean running = new AtomicBoolean();

    public RuntimeDrainLifecycle(RuntimeDrain drain) {
        this.drain = drain;
    }

    @Override
    public void start() {
        running.set(true);
    }

    @Override
    public void stop() {
        drain.setDraining(true);
        running.set(false);
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 16;
    }
}
