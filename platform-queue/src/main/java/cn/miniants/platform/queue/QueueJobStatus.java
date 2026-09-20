package cn.miniants.platform.queue;

/**
 * {@link QueueMode#JOBS} 状态机。
 */
public final class QueueJobStatus {

    public static final String QUEUED = "QUEUED";
    public static final String DELAYED = "DELAYED";
    public static final String PROCESSING = "PROCESSING";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";

    private QueueJobStatus() {
    }

    public static boolean isTerminal(String status) {
        return SUCCEEDED.equals(status) || FAILED.equals(status);
    }
}
