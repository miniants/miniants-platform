package cn.miniants.platform.queue;

/**
 * 连续队列 Redis 键布局。调用方注入前缀，内核不硬编码产品前缀。
 */
public interface QueueKeyFactory {

    String delay(String queueName);

    String ready(String queueName);

    String ready(String queueName, String instanceId);

    String wake(String queueName);

    String job(String queueName, String jobId);

    String lock(String queueName, String jobId);

    String index(String queueName);

    String indexBy(String queueName, String field, String value);
}
