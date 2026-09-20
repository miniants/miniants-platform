package cn.miniants.platform.integration;

import cn.miniants.platform.integration.idempotency.IdempotencyStore;
import cn.miniants.platform.integration.idempotency.LocalIdempotencyStore;
import cn.miniants.platform.integration.idempotency.RedisIdempotencyStore;
import cn.miniants.platform.integration.lock.DistributedLock;
import cn.miniants.platform.integration.lock.LocalDistributedLock;
import cn.miniants.platform.integration.lock.RedisDistributedLock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PlatformIntegrationAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    PlatformIntegrationRedisAutoConfiguration.class,
                    PlatformIntegrationAutoConfiguration.class));

    @Test
    void withoutRedisFallsBackToSingleProcessImplementations() {
        runner.run(context -> {
            assertThat(context).getBean(DistributedLock.class).isInstanceOf(LocalDistributedLock.class);
            assertThat(context).getBean(IdempotencyStore.class).isInstanceOf(LocalIdempotencyStore.class);
        });
    }

    /** 顺序错了不会报错，只会静默退化成单机语义，多实例下互斥失效。 */
    @Test
    void redisImplementationsWinOverTheSingleProcessFallback() {
        runner.withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class)).run(context -> {
            assertThat(context).getBean(DistributedLock.class).isInstanceOf(RedisDistributedLock.class);
            assertThat(context).getBean(IdempotencyStore.class).isInstanceOf(RedisIdempotencyStore.class);
        });
    }

    @Test
    void redisImplementationsCanBeTurnedOffWhileKeepingTheRest() {
        runner.withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withPropertyValues("platform.integration.redis.enabled=false")
                .run(context -> assertThat(context)
                        .getBean(DistributedLock.class).isInstanceOf(LocalDistributedLock.class));
    }

    @Test
    void disablingTheModuleRegistersNothing() {
        runner.withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withPropertyValues("platform.integration.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(DistributedLock.class)
                        .doesNotHaveBean(IdempotencyStore.class));
    }

    @Test
    void oldRateLimitPackageIsGone() {
        assertThat(onClasspath("cn.miniants.platform.integration.ratelimit.RateLimiter")).isFalse();
        assertThat(onClasspath("cn.miniants.platform.integration.ratelimit.LocalRateLimiter")).isFalse();
        assertThat(onClasspath("cn.miniants.platform.integration.ratelimit.RedisRateLimiter")).isFalse();
    }

    private static boolean onClasspath(String className) {
        try {
            Class.forName(className, false, PlatformIntegrationAutoConfigurationTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    @Test
    void applicationSuppliedImplementationBeatsBoth() {
        DistributedLock custom = new LocalDistributedLock();
        runner.withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(DistributedLock.class, () -> custom)
                .run(context -> assertThat(context).getBean(DistributedLock.class).isSameAs(custom));
    }
}
