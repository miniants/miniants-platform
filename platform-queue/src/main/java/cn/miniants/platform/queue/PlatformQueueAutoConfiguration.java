package cn.miniants.platform.queue;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 队列实例得带各自的处理器，内核建不了；这里只提供大家共用的那一件——键前缀。
 *
 * <p>同一进程里两条队列用了不同前缀会各扫各的键，谁都不报错。所以前缀该是一处定义、到处注入。
 */
@AutoConfiguration(after = DataRedisAutoConfiguration.class)
@ConditionalOnClass(StringRedisTemplate.class)
@EnableConfigurationProperties(PlatformQueueProperties.class)
public class PlatformQueueAutoConfiguration {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(QueueKeyFactory.class)
    @ConditionalOnProperty(prefix = "platform.queue", name = "key-prefix")
    public QueueKeyFactory platformQueueKeyFactory(PlatformQueueProperties properties) {
        return new PrefixedQueueKeyFactory(properties.getKeyPrefix());
    }
}
