package cn.miniants.platform.ratelimit.redis.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.ClassUtils;

/**
 * {@code backend=redis} 时即使 Spring Data Redis 不在 classpath 也必须给出明确启动错误。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "platform.ratelimit", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "platform.ratelimit", name = "backend", havingValue = "redis")
public class PlatformRateLimitRedisBackendGuardAutoConfiguration {

    public PlatformRateLimitRedisBackendGuardAutoConfiguration() {
        ClassLoader loader = getClass().getClassLoader();
        if (!ClassUtils.isPresent("org.springframework.data.redis.core.StringRedisTemplate", loader)) {
            throw new IllegalStateException(
                    "platform.ratelimit.backend=redis，但 classpath 上没有 StringRedisTemplate。"
                            + "请引入 spring-boot-starter-data-redis 并正确配置 Redis 连接");
        }
    }
}
