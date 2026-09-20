package cn.miniants.platform.queue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link QueueMode#JOBS} 任务只读视图。
 */
public final class QueueJob {

    private final String jobId;
    private final Map<String, String> fields;

    public QueueJob(String jobId, Map<String, String> fields) {
        this.jobId = jobId == null ? "" : jobId;
        this.fields = fields == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public String jobId() {
        return jobId;
    }

    public Map<String, String> fields() {
        return fields;
    }

    public String get(String key) {
        String value = fields.get(key);
        return value == null ? "" : value;
    }

    public String status() {
        return get(QueueJobFields.STATUS);
    }

    public int attempt() {
        return parseInt(get(QueueJobFields.ATTEMPT));
    }

    public int maxAttempts() {
        return parseInt(get(QueueJobFields.MAX_ATTEMPTS));
    }

    public String lastError() {
        return get(QueueJobFields.LAST_ERROR);
    }

    public String enqueuedAt() {
        return get(QueueJobFields.ENQUEUED_AT);
    }

    public String updatedAt() {
        return get(QueueJobFields.UPDATED_AT);
    }

    public String lockedBy() {
        return get(QueueJobFields.LOCKED_BY);
    }

    public boolean isTerminal() {
        return QueueJobStatus.isTerminal(status());
    }

    static int parseInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
