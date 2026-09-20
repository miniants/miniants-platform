package cn.miniants.platform.integration.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Redis Pub/Sub 版失效总线。
 *
 * <p>channel 由构造参数给定：所有实例必须用同一个字符串，否则总线会静默劈成两半——
 * 滚动发布期间新老实例各订阅一个 channel，看不出错，只是缓存不再失效。
 */
public class RedisInvalidationBus implements InvalidationBus, MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisInvalidationBus.class);

    private final StringRedisTemplate redis;
    private final String channel;
    private final Map<String, List<Runnable>> listeners = new ConcurrentHashMap<>();

    public RedisInvalidationBus(StringRedisTemplate redis, String channel) {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("失效总线 channel 不能为空");
        }
        this.redis = redis;
        this.channel = channel.trim();
    }

    public String channel() {
        return channel;
    }

    @Override
    public void publish(String topic) {
        if (topic == null || topic.isBlank() || redis == null) {
            return;
        }
        try {
            redis.convertAndSend(channel, topic.trim());
        } catch (RuntimeException e) {
            log.warn("发布失效通知失败 topic={}: {}", topic, e.getMessage());
        }
    }

    @Override
    public void onInvalidate(String topic, Runnable action) {
        if (topic == null || topic.isBlank() || action == null) {
            return;
        }
        listeners.computeIfAbsent(topic.trim(), k -> new CopyOnWriteArrayList<>()).add(action);
    }

    public void bind(RedisMessageListenerContainer container) {
        container.addMessageListener(this, new ChannelTopic(channel));
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String topic = decode(message);
        if (topic == null || topic.isBlank()) {
            return;
        }
        List<Runnable> actions = listeners.get(topic);
        if (actions == null || actions.isEmpty()) {
            return;
        }
        for (Runnable action : actions) {
            try {
                action.run();
            } catch (RuntimeException e) {
                log.warn("处理失效通知失败 topic={}: {}", topic, e.getMessage());
            }
        }
    }

    private static String decode(Message message) {
        if (message == null || message.getBody() == null) {
            return null;
        }
        byte[] body = message.getBody();
        try {
            // StringRedisTemplate 发出的是 UTF-8 明文；兼容偶发带序列化包装
            String raw = RedisSerializer.string().deserialize(body);
            return raw == null ? new String(body, StandardCharsets.UTF_8).trim() : raw.trim();
        } catch (RuntimeException e) {
            return new String(body, StandardCharsets.UTF_8).trim();
        }
    }
}
