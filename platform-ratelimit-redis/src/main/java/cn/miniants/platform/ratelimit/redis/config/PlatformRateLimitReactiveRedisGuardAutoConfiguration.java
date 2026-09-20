package cn.miniants.platform.ratelimit.redis.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.util.ClassUtils;

/**
 * WebFlux + redis 禁止同步 Redis 兜底。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(prefix = "platform.ratelimit", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "platform.ratelimit", name = "backend", havingValue = "redis")
public class PlatformRateLimitReactiveRedisGuardAutoConfiguration {

    @Bean
    public RateLimitReactiveRedisPresence rateLimitReactiveRedisPresence() {
        ClassLoader loader = getClass().getClassLoader();
        if (!ClassUtils.isPresent("org.springframework.data.redis.core.ReactiveStringRedisTemplate", loader)) {
            throw new IllegalStateException(
                    "WebFlux 使用 platform.ratelimit.backend=redis 时需要 ReactiveStringRedisTemplate。"
                            + "请引入 spring-boot-starter-data-redis-reactive");
        }
        return new RateLimitReactiveRedisPresence();
    }

    public static final class RateLimitReactiveRedisPresence {
    }
}
