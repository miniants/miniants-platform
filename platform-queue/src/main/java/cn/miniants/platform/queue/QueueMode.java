package cn.miniants.platform.queue;

/**
 * 连续队列实例模式。
 */
public enum QueueMode {
    /** 任务 Hash + 锁 + 索引 + 快照 */
    JOBS,
    /** 只有 delay / ready，member 即业务 id */
    ALARM
}
