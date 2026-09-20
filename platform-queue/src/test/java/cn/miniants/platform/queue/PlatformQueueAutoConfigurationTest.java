package cn.miniants.platform.queue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PlatformQueueAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PlatformQueueAutoConfiguration.class))
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class));

    /** 前缀写错等于换了一套键，在途任务全变孤儿。所以不给默认值，宁可不注册。 */
    @Test
    void withoutAnExplicitPrefixNothingIsRegistered() {
        runner.run(context -> assertThat(context).doesNotHaveBean(QueueKeyFactory.class));
    }

    @Test
    void configuredPrefixBecomesTheSharedKeyFactory() {
        runner.withPropertyValues("platform.queue.key-prefix=myapp:queue:")
                .run(context -> assertThat(context.getBean(QueueKeyFactory.class).ready("iot-cmd"))
                        .isEqualTo("myapp:queue:iot-cmd:ready"));
    }

    @Test
    void prefixWithoutTrailingColonFailsFastInsteadOfBuildingWrongKeys() {
        runner.withPropertyValues("platform.queue.key-prefix=myapp:queue")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void applicationSuppliedFactoryWins() {
        QueueKeyFactory custom = new PrefixedQueueKeyFactory("custom:");
        runner.withPropertyValues("platform.queue.key-prefix=myapp:queue:")
                .withBean(QueueKeyFactory.class, () -> custom)
                .run(context -> assertThat(context.getBean(QueueKeyFactory.class)).isSameAs(custom));
    }
}
