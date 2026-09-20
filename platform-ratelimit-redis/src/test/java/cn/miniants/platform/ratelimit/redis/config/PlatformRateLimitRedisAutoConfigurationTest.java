package cn.miniants.platform.ratelimit.redis.config;

import cn.miniants.platform.ratelimit.spi.RateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformRateLimitRedisAutoConfigurationTest {

    @Test
    void failsWhenStringRedisTemplateMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PlatformRateLimitRedisAutoConfiguration.class))
                .withPropertyValues(
                        "platform.ratelimit.enabled=true",
                        "platform.ratelimit.backend=redis",
                        "platform.ratelimit.key-prefix=platform:ratelimit:")
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable failure = context.getStartupFailure();
                    assertThat(failure).isInstanceOf(BeanCreationException.class);
                    assertThat(rootMessage(failure)).contains("StringRedisTemplate");
                });
    }

    @Test
    void inactiveWhenBackendLocal() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PlatformRateLimitRedisAutoConfiguration.class))
                .withPropertyValues("platform.ratelimit.backend=local")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(RateLimiter.class);
                });
    }

    private static String rootMessage(Throwable failure) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = failure;
        while (cur != null) {
            if (cur.getMessage() != null) {
                sb.append(cur.getMessage()).append('\n');
            }
            cur = cur.getCause();
        }
        return sb.toString();
    }
}
