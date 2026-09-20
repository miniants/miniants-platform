package cn.miniants.platform.ratelimit.redis.snapshot;

import cn.miniants.platform.ratelimit.redis.RateLimitRedisKeys;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.nio.charset.StandardCharsets;

/**
 * 订阅策略事件通道，触发数据面快照刷新。
 */
public class RateLimitPolicySnapshotListener implements MessageListener {

    private final RateLimitPolicySnapshotRefreshService refreshService;

    public RateLimitPolicySnapshotListener(RateLimitPolicySnapshotRefreshService refreshService) {
        this.refreshService = refreshService;
    }

    public void bind(RedisMessageListenerContainer container, String keyPrefix) {
        String channel = RateLimitRedisKeys.policyEventsChannel(keyPrefix);
        container.addMessageListener(this, new ChannelTopic(channel));
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        refreshService.onEvent(decode(message));
    }

    private static String decode(Message message) {
        if (message == null || message.getBody() == null) {
            return null;
        }
        try {
            String raw = RedisSerializer.string().deserialize(message.getBody());
            return raw == null ? new String(message.getBody(), StandardCharsets.UTF_8).trim() : raw.trim();
        } catch (RuntimeException ex) {
            return new String(message.getBody(), StandardCharsets.UTF_8).trim();
        }
    }
}
