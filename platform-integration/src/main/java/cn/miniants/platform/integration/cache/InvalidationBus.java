package cn.miniants.platform.integration.cache;

/**
 * 本地缓存失效广播。写完共享数据后 {@link #publish}，各实例把对应的
 * {@link LocalTtlCache} 清掉，不必把 TTL 压到很短。
 *
 * <p>topic 是业务键名，不是 Redis channel——channel 由实现决定。
 */
public interface InvalidationBus {

    void publish(String topic);

    void onInvalidate(String topic, Runnable action);
}
