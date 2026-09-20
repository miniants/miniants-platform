package cn.miniants.platform.queue;

/**
 * 场景处理契约。组件负责弹出 jobId；成功 / 失败 / 延迟由场景调用 {@link ContinuousQueue}。
 */
@FunctionalInterface
public interface QueueHandler {

    void process(String jobId);
}
