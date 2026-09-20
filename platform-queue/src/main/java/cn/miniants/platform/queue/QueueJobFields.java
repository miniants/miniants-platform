package cn.miniants.platform.queue;

/**
 * job Hash 中由组件写入的字段。其余键视为场景 payload。
 */
public final class QueueJobFields {

    public static final String JOB_ID = "jobId";
    public static final String STATUS = "status";
    public static final String ATTEMPT = "attempt";
    public static final String MAX_ATTEMPTS = "maxAttempts";
    public static final String LAST_ERROR = "lastError";
    public static final String ENQUEUED_AT = "enqueuedAt";
    public static final String UPDATED_AT = "updatedAt";
    public static final String LOCKED_BY = "lockedBy";

    private QueueJobFields() {
    }
}
