package cn.miniants.platform.ratelimit.redis.config;

import cn.miniants.platform.ratelimit.RateLimitBackend;
import cn.miniants.platform.ratelimit.config.RateLimitProperties;
import cn.miniants.platform.ratelimit.local.StaleAwareRateLimiter;
import cn.miniants.platform.ratelimit.local.StaleAwareReactiveRateLimiter;
import cn.miniants.platform.ratelimit.metrics.RateLimitMetricsRecorder;
import cn.miniants.platform.ratelimit.policy.CompositeRateLimitPolicyRegistry;
import cn.miniants.platform.ratelimit.redis.ReactiveRedisRateLimiter;
import cn.miniants.platform.ratelimit.redis.RedisRateLimitBucketInspector;
import cn.miniants.platform.ratelimit.redis.RedisRateLimiter;
import cn.miniants.platform.ratelimit.spi.RateLimitBucketInspector;
import cn.miniants.platform.ratelimit.spi.RateLimitPolicyRegistry;
import cn.miniants.platform.ratelimit.spi.RateLimiter;
import cn.miniants.platform.ratelimit.spi.ReactiveRateLimiter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

/**
 * Redis 限流后端自动配置。仅当 {@code platform.ratelimit.backend=redis} 时生效。
 */
@AutoConfiguration(after = {
        DataRedisAutoConfiguration.class,
        DataRedisReactiveAutoConfiguration.class
})
@ConditionalOnClass(StringRedisTemplate.class)
@EnableConfigurationProperties(RateLimitProperties.class)
@ConditionalOnProperty(prefix = "platform.ratelimit", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "platform.ratelimit", name = "backend", havingValue = "redis")
public class PlatformRateLimitRedisAutoConfiguration {

    @Bean
    @Primary
    @ConditionalOnMissingBean(RateLimiter.class)
    public RateLimiter redisRateLimiter(
            ObjectProvider<StringRedisTemplate> redisProvider,
            RateLimitProperties properties,
            ObjectProvider<RateLimitPolicyRegistry> registry,
            ObjectProvider<RateLimitMetricsRecorder> metrics) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            throw new IllegalStateException(
                    "platform.ratelimit.backend=redis，但未找到 StringRedisTemplate。"
                            + "请引入 spring-boot-starter-data-redis 并正确配置 Redis 连接");
        }
        String keyPrefix = requireKeyPrefix(properties);
        return new StaleAwareRateLimiter(
                new RedisRateLimiter(redis, keyPrefix),
                registry.getIfAvailable(() -> new CompositeRateLimitPolicyRegistry(List.of())),
                properties,
                metrics.getIfAvailable(() -> RateLimitMetricsRecorder.NOOP),
                RateLimitBackend.REDIS);
    }

    @Bean
    @Primary
    @ConditionalOnBean(ReactiveStringRedisTemplate.class)
    @ConditionalOnMissingBean(ReactiveRateLimiter.class)
    public ReactiveRateLimiter reactiveRedisRateLimiter(
            ReactiveStringRedisTemplate redis,
            RateLimitProperties properties,
            ObjectProvider<RateLimitPolicyRegistry> registry,
            ObjectProvider<RateLimitMetricsRecorder> metrics) {
        String keyPrefix = requireKeyPrefix(properties);
        return new StaleAwareReactiveRateLimiter(
                new ReactiveRedisRateLimiter(redis, keyPrefix),
                registry.getIfAvailable(() -> new CompositeRateLimitPolicyRegistry(List.of())),
                properties,
                metrics.getIfAvailable(() -> RateLimitMetricsRecorder.NOOP),
                RateLimitBackend.REDIS);
    }

    @Bean
    @ConditionalOnMissingBean(RateLimitBucketInspector.class)
    public RateLimitBucketInspector redisRateLimitBucketInspector(
            ObjectProvider<StringRedisTemplate> redisProvider,
            RateLimitProperties properties,
            ObjectProvider<RateLimitPolicyRegistry> registry) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            throw new IllegalStateException(
                    "platform.ratelimit.backend=redis，但未找到 StringRedisTemplate，无法列出限流桶");
        }
        return new RedisRateLimitBucketInspector(
                redis,
                requireKeyPrefix(properties),
                registry.getIfAvailable(() -> new CompositeRateLimitPolicyRegistry(List.of())));
    }

    private static String requireKeyPrefix(RateLimitProperties properties) {
        String keyPrefix = properties.getKeyPrefix();
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalStateException("platform.ratelimit.key-prefix 不能为空");
        }
        return keyPrefix;
    }
}
