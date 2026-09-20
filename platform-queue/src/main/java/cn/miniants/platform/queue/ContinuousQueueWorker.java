package cn.miniants.platform.queue;

import cn.miniants.platform.ops.RuntimeDrain;
import cn.miniants.platform.ops.RuntimeStateContributor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

/**
 * 连续队列工人：BRPOP + delay 闹钟 + 孤儿回收。挂到 {@link ContinuousQueue} 实例上，不要继承。
 */
public final class ContinuousQueueWorker implements RuntimeStateContributor {

    private static final Logger log = LoggerFactory.getLogger(ContinuousQueueWorker.class);

    private static final long POLL_TIMEOUT_SECONDS = 5L;
    private static final long RECLAIM_INTERVAL_MS = 20_000L;

    private final ContinuousQueue queue;
    private final String threadNamePrefix;
    private final IntSupplier concurrencySupplier;
    private final RedisMessageListenerContainer listenerContainer;
    private final RuntimeDrain processDrain;

    private final AtomicInteger desiredConcurrency;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicInteger workerSeq = new AtomicInteger();
    private final ConcurrentHashMap<Integer, Boolean> activeSlots = new ConcurrentHashMap<>();
    private final ExecutorService workerExecutor;
    private RedisDelayClock delayClock;
    private Thread reclaimThread;

    public ContinuousQueueWorker(ContinuousQueue queue, RedisMessageListenerContainer listenerContainer) {
        this(queue, listenerContainer, null);
    }

    public ContinuousQueueWorker(ContinuousQueue queue, RedisMessageListenerContainer listenerContainer,
            RuntimeDrain processDrain) {
        if (queue == null) {
            throw new IllegalArgumentException("ContinuousQueue 不能为空");
        }
        this.queue = queue;
        this.threadNamePrefix = queue.name() + "-";
        this.concurrencySupplier = queue.spec().concurrency();
        this.listenerContainer = listenerContainer;
        this.processDrain = processDrain;
        this.desiredConcurrency = new AtomicInteger(Math.max(1, concurrencySupplier.getAsInt()));
        this.workerExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setName(threadNamePrefix + workerSeq.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public String getName() {
        return queue.name();
    }

    @Override
    public boolean supportsDrain() {
        return true;
    }

    @Override
    public void setDraining(boolean draining) {
        if (draining) {
            beginDrain();
        }
    }

    @Override
    public Map<String, Object> getState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("running", running.get());
        state.put("draining", shouldStopPolling());
        state.put("mode", queue.spec().mode().name());
        state.put("activeSlots", activeSlots.size());
        state.put("desiredConcurrency", desiredConcurrency.get());
        return state;
    }

    @PostConstruct
    public void start() {
        delayClock = new RedisDelayClock(queue.name(), queue, queue.lifecycle(), listenerContainer, queue.wakeChannel());
        delayClock.start();
        ensureWorkers(desiredConcurrency.get());
        reclaimThread = new Thread(this::reclaimLoop, threadNamePrefix + "reclaim");
        reclaimThread.setDaemon(true);
        reclaimThread.start();
        log.info("{} 已启动", queue.name());
    }

    public void beginDrain() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        desiredConcurrency.set(0);
        queue.beginShutdown();
        if (delayClock != null) {
            delayClock.stop();
        }
        log.info("{} 开始排空（停领，等待在办结束）", queue.name());
    }

    private void ensureWorkers(int want) {
        for (int slot = 0; slot < want; slot++) {
            if (activeSlots.putIfAbsent(slot, Boolean.TRUE) != null) {
                continue;
            }
            final int workerSlot = slot;
            workerExecutor.execute(() -> runWorker(workerSlot));
        }
    }

    private void runWorker(int slot) {
        log.info("{} 工人 #{} 启动", queue.name(), slot);
        try {
            while (running.get()
                    && slot < desiredConcurrency.get()
                    && !shouldStopPolling()) {
                try {
                    String jobId = queue.pollReady(POLL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    if (shouldStopPolling()) {
                        break;
                    }
                    if (jobId == null || jobId.isBlank()) {
                        continue;
                    }
                    queue.processJob(jobId);
                } catch (Exception ex) {
                    if (!running.get() || isShutdownNoise(ex)) {
                        log.debug("{} 工人 #{} 关闭中退出: {}", queue.name(), slot, ex.toString());
                        break;
                    }
                    log.error("{} 工人 #{} 处理异常", queue.name(), slot, ex);
                }
            }
        } finally {
            activeSlots.remove(slot);
            log.info("{} 工人 #{} 退出", queue.name(), slot);
        }
    }

    private void reclaimLoop() {
        while (running.get() && !shouldStopPolling()) {
            try {
                Thread.sleep(RECLAIM_INTERVAL_MS);
                if (!running.get() || shouldStopPolling()) {
                    break;
                }
                queue.reclaimOrphanJobs();
                int want = Math.max(1, concurrencySupplier.getAsInt());
                int prev = desiredConcurrency.getAndSet(want);
                if (prev != want && running.get()) {
                    log.info("{} 目标并发 {} -> {}", queue.name(), prev, want);
                    ensureWorkers(want);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                if (isShutdownNoise(ex)) {
                    break;
                }
                log.warn("{} 孤儿回收异常", queue.name(), ex);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        beginDrain();
        if (reclaimThread != null) {
            reclaimThread.interrupt();
        }
        workerExecutor.shutdownNow();
        try {
            workerExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean shouldStopPolling() {
        return queue.shouldStopPolling() || (processDrain != null && processDrain.isDraining());
    }

    private static boolean isShutdownNoise(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            String msg = String.valueOf(t.getMessage());
            if (msg.contains("STOPPING") || msg.contains("STOPPED") || msg.contains("Connection closed")) {
                return true;
            }
            if (t instanceof InterruptedException) {
                return true;
            }
        }
        return false;
    }
}
