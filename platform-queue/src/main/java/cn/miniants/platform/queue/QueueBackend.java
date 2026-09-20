package cn.miniants.platform.queue;

import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

/**
 * 连续队列工人内部契约，由 {@link ContinuousQueue} 实现。场景不要自己实现。
 */
interface QueueBackend {

    void beginShutdown();

    boolean shouldStopPolling();

    /**
     * 阻塞弹出一条就绪任务 id；无任务或应停止时返回 null。
     */
    String pollReady(long timeout, TimeUnit unit);

    /**
     * 若 delay 里最早一条已到期，原子挪到 ready，返回是否推进了一条。
     */
    boolean promoteFirstDue();

    /**
     * delay 最早一条的 score（毫秒）；空则 empty。
     */
    OptionalLong peekDelayScore();

    void reclaimOrphanJobs();

    void processJob(String jobId);
}
