package cn.miniants.platform.integration.cache;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 没有 Redis 时的退化实现：只通知本进程。
 *
 * <p>单实例部署下语义完整。多实例部署下别的进程收不到通知，只会等 TTL 到期——
 * 不报错，所以多实例务必确认 {@link RedisInvalidationBus} 真的装上了。
 */
public class LocalInvalidationBus implements InvalidationBus {

    private final Map<String, List<Runnable>> listeners = new ConcurrentHashMap<>();

    @Override
    public void publish(String topic) {
        if (topic == null || topic.isBlank()) {
            return;
        }
        List<Runnable> actions = listeners.get(topic.trim());
        if (actions == null) {
            return;
        }
        actions.forEach(action -> {
            try {
                action.run();
            } catch (RuntimeException ex) {
                // 与 Redis 版一致：单个 listener 失败不影响其余订阅者
            }
        });
    }

    @Override
    public void onInvalidate(String topic, Runnable action) {
        if (topic == null || topic.isBlank() || action == null) {
            return;
        }
        listeners.computeIfAbsent(topic.trim(), k -> new CopyOnWriteArrayList<>()).add(action);
    }
}
