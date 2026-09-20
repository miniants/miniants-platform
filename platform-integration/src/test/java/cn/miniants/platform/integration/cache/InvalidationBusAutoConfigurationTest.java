package cn.miniants.platform.integration.cache;

import cn.miniants.platform.integration.PlatformIntegrationAutoConfiguration;
import cn.miniants.platform.integration.PlatformIntegrationRedisAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 只验条件分支。「容器真的订阅上了」需要一个活的 Redis，本模块的测试栈没有——所有
 * Redis 用例都是 mock {@code StringRedisTemplate}，这里不去伪造一条能 subscribe 的连接。
 *
 * <p>因此凡是会让内核自己建容器的分支，都不放 {@link RedisConnectionFactory}：容器一旦被建出来
 * 就会在 refresh 时自启并去连 Redis，而 3.3 的容器没有关掉自启的开关。
 */
class InvalidationBusAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    PlatformIntegrationRedisAutoConfiguration.class,
                    PlatformIntegrationAutoConfiguration.class));

    private ApplicationContextRunner withRedisTemplateOnly() {
        return runner.withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class));
    }

    @Test
    void withoutRedisFallsBackToInProcessOnly() {
        runner.run(context -> {
            assertThat(context).getBean(InvalidationBus.class).isInstanceOf(LocalInvalidationBus.class);
            assertThat(context).doesNotHaveBean(RedisMessageListenerContainer.class);
        });
    }

    @Test
    void redisImplementationWinsOverTheInProcessFallback() {
        withRedisTemplateOnly().run(context -> assertThat(context)
                .getBean(InvalidationBus.class).isInstanceOf(RedisInvalidationBus.class));
    }

    /** 默认 channel 跟着 key-prefix 走，同一 Redis 上多个应用才不会互相收到对方的失效。 */
    @Test
    void channelDefaultsToTheKeyPrefix() {
        withRedisTemplateOnly().withPropertyValues("platform.integration.redis.key-prefix=acme:")
                .run(context -> assertThat(context.getBean(RedisInvalidationBus.class).channel())
                        .isEqualTo("acme:bus:invalidate"));
    }

    /** 已经在跑的应用要能钉住现网 channel，不能被内核默认值改掉。 */
    @Test
    void channelCanBePinnedToAnExistingOne() {
        withRedisTemplateOnly()
                .withPropertyValues("platform.integration.redis.invalidate-channel=jwy:sys:bus:invalidate")
                .run(context -> assertThat(context.getBean(RedisInvalidationBus.class).channel())
                        .isEqualTo("jwy:sys:bus:invalidate"));
    }

    /**
     * 应用自带容器时内核不能再建一个：同一进程订阅同一 channel 两次，每条失效通知会被处理两遍，
     * 表现不出错误，只是缓存莫名多刷一次。
     */
    @Test
    void doesNotAddASecondListenerContainer() {
        withRedisTemplateOnly()
                .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .withBean("appContainer", RedisMessageListenerContainer.class, () -> {
                    RedisMessageListenerContainer container = new NeverStartingContainer();
                    container.setConnectionFactory(mock(RedisConnectionFactory.class));
                    return container;
                })
                .run(context -> assertThat(context).hasSingleBean(RedisMessageListenerContainer.class));
    }

    /** 换了非 Redis 实现就不该建容器——那条订阅连接没人用。 */
    @Test
    void aCustomBusSuppressesTheRedisContainer() {
        InvalidationBus custom = new LocalInvalidationBus();
        withRedisTemplateOnly()
                .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .withBean(InvalidationBus.class, () -> custom)
                .run(context -> {
                    assertThat(context).getBean(InvalidationBus.class).isSameAs(custom);
                    assertThat(context).doesNotHaveBean(RedisMessageListenerContainer.class);
                });
    }

    /** 容器是 SmartLifecycle，refresh 时会自启去连 Redis；测试里只需要它作为一个 bean 存在。 */
    static class NeverStartingContainer extends RedisMessageListenerContainer {

        @Override
        public void start() {
        }
    }
}
