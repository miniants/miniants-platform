package cn.miniants.platform.queue;

import java.time.Duration;
import java.util.function.IntSupplier;
import java.util.regex.Pattern;

/**
 * 一条连续队列的场景配置（不可变）。由 {@link ContinuousQueue.Builder} 构建。
 */
public final class QueueSpec {

    static final Pattern NAME = Pattern.compile("^[a-z][a-z0-9-]{0,62}$");

    private final String name;
    private final String logLabel;
    private final QueueMode mode;
    private final int maxAttempts;
    private final Duration lockTtl;
    private final Duration queuedStale;
    private final Duration activeTtl;
    private final Duration terminalTtl;
    private final Duration failedTtl;
    private final String indexField;
    private final IntSupplier concurrency;

    QueueSpec(String name, String logLabel, QueueMode mode, int maxAttempts,
            Duration lockTtl, Duration queuedStale, Duration activeTtl,
            Duration terminalTtl, Duration failedTtl, String indexField, IntSupplier concurrency) {
        this.name = name;
        this.logLabel = logLabel;
        this.mode = mode;
        this.maxAttempts = maxAttempts;
        this.lockTtl = lockTtl;
        this.queuedStale = queuedStale;
        this.activeTtl = activeTtl;
        this.terminalTtl = terminalTtl;
        this.failedTtl = failedTtl;
        this.indexField = indexField;
        this.concurrency = concurrency;
    }

    public String name() {
        return name;
    }

    public String logLabel() {
        return logLabel;
    }

    public QueueMode mode() {
        return mode;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public Duration lockTtl() {
        return lockTtl;
    }

    public Duration queuedStale() {
        return queuedStale;
    }

    public Duration activeTtl() {
        return activeTtl;
    }

    public Duration terminalTtl() {
        return terminalTtl;
    }

    public Duration failedTtl() {
        return failedTtl;
    }

    public String indexField() {
        return indexField;
    }

    public IntSupplier concurrency() {
        return concurrency;
    }

    static void requireName(String name) {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("队列名须为小写字母开头的短横线名: " + name);
        }
    }
}
