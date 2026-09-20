package cn.miniants.platform.ratelimit.redis.config;

import cn.miniants.platform.ratelimit.config.PlatformRateLimitCoreAutoConfiguration;
import cn.miniants.platform.ratelimit.config.RateLimitProperties;
import cn.miniants.platform.ratelimit.policy.CompositeRateLimitPolicyRegistry;
import cn.miniants.platform.ratelimit.redis.snapshot.RateLimitPolicySnapshotListener;
import cn.miniants.platform.ratelimit.redis.snapshot.RateLimitPolicySnapshotReconcileScheduler;
import cn.miniants.platform.ratelimit.redis.snapshot.RateLimitPolicySnapshotRefreshService;
import cn.miniants.platform.ratelimit.snapshot.RateLimitPolicySnapshotCodec;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 普通数据面节点消费动态策略：启动拉取、Pub/Sub、周期对账。
 */
@AutoConfiguration(after = {
        PlatformRateLimitCoreAutoConfiguration.class,
        PlatformRateLimitRedisAutoConfiguration.class
})
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnBean({StringRedisTemplate.class, CompositeRateLimitPolicyRegistry.class})
@ConditionalOnProperty(prefix = "platform.ratelimit", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "platform.ratelimit", name = "backend", havingValue = "redis")
@EnableConfigurationProperties(RateLimitProperties.class)
@EnableScheduling
public class PlatformRateLimitDynamicPolicyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RateLimitPolicySnapshotRefreshService rateLimitPolicySnapshotRefreshService(
            StringRedisTemplate redisTemplate,
            RateLimitPolicySnapshotCodec codec,
            CompositeRateLimitPolicyRegistry registry,
            RateLimitProperties properties) {
        return new RateLimitPolicySnapshotRefreshService(redisTemplate, codec, registry, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitPolicySnapshotReconcileScheduler rateLimitPolicySnapshotReconcileScheduler(
            RateLimitPolicySnapshotRefreshService refreshService) {
        return new RateLimitPolicySnapshotReconcileScheduler(refreshService);
    }

    @Bean
    @ConditionalOnBean(RedisMessageListenerContainer.class)
    @ConditionalOnMissingBean
    public RateLimitPolicySnapshotListener rateLimitPolicySnapshotListener(
            RateLimitPolicySnapshotRefreshService refreshService,
            RedisMessageListenerContainer container,
            RateLimitProperties properties) {
        RateLimitPolicySnapshotListener listener = new RateLimitPolicySnapshotListener(refreshService);
        listener.bind(container, properties.getKeyPrefix());
        return listener;
    }

    @Bean
    public ApplicationRunner rateLimitPolicySnapshotStartupLoader(
            RateLimitPolicySnapshotRefreshService refreshService) {
        return args -> refreshService.refreshFromRedis();
    }
}
