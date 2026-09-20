package cn.miniants.platform.queue;

/**
 * {@code prefix + queueName + :ready|delay|...}。{@code prefix} 须以 {@code :} 结尾。
 */
public final class PrefixedQueueKeyFactory implements QueueKeyFactory {

    private final String prefix;

    public PrefixedQueueKeyFactory(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("队列键前缀不能为空");
        }
        if (!prefix.endsWith(":")) {
            throw new IllegalArgumentException("队列键前缀须以 ':' 结尾");
        }
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }

    @Override
    public String delay(String queueName) {
        return prefix + queueName + ":delay";
    }

    @Override
    public String ready(String queueName) {
        return prefix + queueName + ":ready";
    }

    @Override
    public String ready(String queueName, String instanceId) {
        return prefix + queueName + ":ready:" + instanceId;
    }

    @Override
    public String wake(String queueName) {
        return prefix + queueName + ":wake";
    }

    @Override
    public String job(String queueName, String jobId) {
        return prefix + queueName + ":job:" + jobId;
    }

    @Override
    public String lock(String queueName, String jobId) {
        return prefix + queueName + ":lock:" + jobId;
    }

    @Override
    public String index(String queueName) {
        return prefix + queueName + ":index";
    }

    @Override
    public String indexBy(String queueName, String field, String value) {
        return prefix + queueName + ":by:" + field + ":" + value;
    }
}
