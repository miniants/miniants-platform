package cn.miniants.platform.ratelimit.spi;

import cn.miniants.platform.ratelimit.RateLimitAlgorithm;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.config.PlatformRateLimitCoreAutoConfiguration;
import cn.miniants.platform.ratelimit.policy.CompositeRateLimitPolicyRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitPolicyContributorTest {

    @Test
    void builtinYieldsToYaml() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PlatformRateLimitCoreAutoConfiguration.class))
                .withUserConfiguration(BuiltinConfig.class)
                .withPropertyValues(
                        "platform.ratelimit.policies.authLogin.limit=20",
                        "platform.ratelimit.policies.authLogin.period=1m")
                .run(context -> {
                    CompositeRateLimitPolicyRegistry registry = context.getBean(CompositeRateLimitPolicyRegistry.class);
                    assertThat(registry.require("authLogin").limit()).isEqualTo(20L);
                    assertThat(registry.require("sms.send").limit()).isEqualTo(3L);
                });
    }

    @Configuration
    static class BuiltinConfig {
        @Bean
        RateLimitPolicyContributor builtins() {
            return RateLimitPolicyContributor.of(
                    RateLimitPolicy.builder()
                            .policyCode("authLogin")
                            .algorithm(RateLimitAlgorithm.GCRA)
                            .limit(5)
                            .period(Duration.ofMinutes(1))
                            .build(),
                    RateLimitPolicy.builder()
                            .policyCode("sms.send")
                            .algorithm(RateLimitAlgorithm.GCRA)
                            .limit(3)
                            .period(Duration.ofMinutes(10))
                            .build());
        }
    }
}
