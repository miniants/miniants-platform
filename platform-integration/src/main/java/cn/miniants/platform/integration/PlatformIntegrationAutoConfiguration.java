package cn.miniants.platform.integration;

import cn.miniants.platform.integration.cache.InvalidationBus;
import cn.miniants.platform.integration.cache.LocalInvalidationBus;
import cn.miniants.platform.integration.idempotency.IdempotencyStore;
import cn.miniants.platform.integration.idempotency.LocalIdempotencyStore;
import cn.miniants.platform.integration.lock.DistributedLock;
import cn.miniants.platform.integration.lock.LocalDistributedLock;
import cn.miniants.platform.integration.secret.EnvironmentSecretProvider;
import cn.miniants.platform.integration.secret.SecretProperties;
import cn.miniants.platform.integration.secret.SecretProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 锁 / 幂等的兜底实现，仅在没有 Redis 实现时生效。
 *
 * <p>这几个 Local 实现都是单进程语义，多实例部署下不互斥、通知不到别的实例。见
 * {@link PlatformIntegrationRedisAutoConfiguration}。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "platform.integration", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({IntegrationProperties.class, SecretProperties.class})
public class PlatformIntegrationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SecretProvider.class)
    public SecretProvider secretProvider(SecretProperties properties) {
        return new EnvironmentSecretProvider(properties);
    }

    @Bean
    @ConditionalOnMissingBean(DistributedLock.class)
    public DistributedLock distributedLock() {
        return new LocalDistributedLock();
    }

    @Bean
    @ConditionalOnMissingBean(IdempotencyStore.class)
    public IdempotencyStore idempotencyStore() {
        return new LocalIdempotencyStore();
    }

    @Bean
    @ConditionalOnMissingBean(InvalidationBus.class)
    public InvalidationBus invalidationBus() {
        return new LocalInvalidationBus();
    }

    @Bean
    public IntegrationAssemblyReporter integrationAssemblyReporter(
            DistributedLock lock,
            IdempotencyStore idempotencyStore,
            InvalidationBus invalidationBus) {
        return new IntegrationAssemblyReporter(lock, idempotencyStore, invalidationBus);
    }
}
