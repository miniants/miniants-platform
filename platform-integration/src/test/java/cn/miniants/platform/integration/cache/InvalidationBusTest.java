package cn.miniants.platform.integration.cache;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InvalidationBusTest {

    @Test
    void localBusNotifiesItsOwnProcess() {
        LocalInvalidationBus bus = new LocalInvalidationBus();
        AtomicInteger hits = new AtomicInteger();
        bus.onInvalidate("cfg", hits::incrementAndGet);

        bus.publish("cfg");
        bus.publish("other");

        assertThat(hits.get()).isEqualTo(1);
    }

    @Test
    void redisBusPublishesOnTheConfiguredChannel() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        new RedisInvalidationBus(redis, "app:bus:invalidate").publish("cfg");

        verify(redis).convertAndSend("app:bus:invalidate", "cfg");
    }

    /** 收到消息才刷缓存。payload 是明文 topic，不是序列化对象。 */
    @Test
    void redisBusRunsListenersForTheTopicInThePayload() {
        RedisInvalidationBus bus = new RedisInvalidationBus(mock(StringRedisTemplate.class), "c");
        AtomicInteger hits = new AtomicInteger();
        bus.onInvalidate("cfg", hits::incrementAndGet);

        bus.onMessage(new DefaultMessage("c".getBytes(StandardCharsets.UTF_8),
                "cfg".getBytes(StandardCharsets.UTF_8)), null);

        assertThat(hits.get()).isEqualTo(1);
    }

    /** 一个监听器抛异常不能让同 topic 的其它监听器收不到。 */
    @Test
    void oneFailingListenerDoesNotBlockTheRest() {
        RedisInvalidationBus bus = new RedisInvalidationBus(mock(StringRedisTemplate.class), "c");
        AtomicInteger hits = new AtomicInteger();
        bus.onInvalidate("cfg", () -> {
            throw new IllegalStateException("boom");
        });
        bus.onInvalidate("cfg", hits::incrementAndGet);

        bus.onMessage(new DefaultMessage("c".getBytes(StandardCharsets.UTF_8),
                "cfg".getBytes(StandardCharsets.UTF_8)), null);

        assertThat(hits.get()).isEqualTo(1);
    }

    /** Redis 抽风时不能把业务写操作一起带崩——失效通知丢了顶多等 TTL。 */
    @Test
    void publishFailureIsSwallowed() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        doThrow(new IllegalStateException("down")).when(redis).convertAndSend(any(), any());

        new RedisInvalidationBus(redis, "c").publish("cfg");
    }

    /** 空 channel 会让 publish 静默无效，宁可启动就失败。 */
    @Test
    void blankChannelIsRejectedAtConstruction() {
        assertThatThrownBy(() -> new RedisInvalidationBus(mock(StringRedisTemplate.class), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
