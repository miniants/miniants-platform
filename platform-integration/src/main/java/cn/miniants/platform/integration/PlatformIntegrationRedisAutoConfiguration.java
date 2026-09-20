package cn.miniants.platform.integration;

import cn.miniants.platform.integration.cache.InvalidationBus;
import cn.miniants.platform.integration.cache.RedisInvalidationBus;
import cn.miniants.platform.integration.idempotency.IdempotencyStore;
import cn.miniants.platform.integration.idempotency.RedisIdempotencyStore;
import cn.miniants.platform.integration.lock.DistributedLock;
import cn.miniants.platform.integration.lock.RedisDistributedLock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 有 Redis 就用 Redis 实现锁 / 幂等。
 *
 * <p>必须排在 {@link PlatformIntegrationAutoConfiguration} 之前：那边的单机实现靠
 * {@code @ConditionalOnMissingBean} 让位，顺序反了就会退化成单机语义而不报错。
 */
@AutoConfiguration(
        before = PlatformIntegrationAutoConfiguration.class,
        afterName = "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration")
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnBean(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "platform.integration", name = {"enabled", "redis.enabled"},
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(IntegrationProperties.class)
public class PlatformIntegrationRedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DistributedLock.class)
    public DistributedLock redisDistributedLock(StringRedisTemplate redis, IntegrationProperties properties) {
        return new RedisDistributedLock(redis, properties.getRedis().getKeyPrefix() + "lock:");
    }

    @Bean
    @ConditionalOnMissingBean(IdempotencyStore.class)
    public IdempotencyStore redisIdempotencyStore(StringRedisTemplate redis, IntegrationProperties properties) {
        return new RedisIdempotencyStore(redis, properties.getRedis().getKeyPrefix() + "idem:");
    }

    @Bean
    @ConditionalOnMissingBean(InvalidationBus.class)
    public RedisInvalidationBus redisInvalidationBus(StringRedisTemplate redis, IntegrationProperties properties) {
        return new RedisInvalidationBus(redis, properties.getRedis().invalidateChannelOrDefault());
    }

    /**
     * 只在总线确实是 Redis 版时才建容器：应用换了别的 {@link InvalidationBus} 实现时，
     * 建一个空订阅的容器只是白占一条连接。
     *
     * <p>已有容器就让位——一个进程订阅同一 channel 两次会收到两份消息。
     */
    @Bean
    @ConditionalOnBean({RedisInvalidationBus.class, RedisConnectionFactory.class})
    @ConditionalOnMissingBean(RedisMessageListenerContainer.class)
    public RedisMessageListenerContainer platformRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory, RedisInvalidationBus bus) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        bus.bind(container);
        return container;
    }
}
