package cn.miniants.platform.ratelimit.admin;

import cn.miniants.platform.ratelimit.config.RateLimitProperties;
import cn.miniants.platform.ratelimit.redis.snapshot.RateLimitPolicySnapshotPublisher;
import cn.miniants.platform.ratelimit.redis.snapshot.RedisRateLimitPolicySnapshotPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(StringRedisTemplate.class)
class RateLimitAdminRedisPublishConfiguration {

    @Bean
    @Primary
    @ConditionalOnBean(StringRedisTemplate.class)
    RateLimitPolicySnapshotPublisher redisRateLimitPolicySnapshotPublisher(
            StringRedisTemplate redisTemplate,
            RateLimitProperties properties) {
        return new RedisRateLimitPolicySnapshotPublisher(redisTemplate, properties);
    }
}
