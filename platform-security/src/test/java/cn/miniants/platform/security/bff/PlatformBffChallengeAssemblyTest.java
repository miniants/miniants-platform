package cn.miniants.platform.security.bff;

import cn.miniants.platform.security.challenge.LoginChallenge;
import cn.miniants.platform.security.challenge.RedisSliderCaptchaService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * challenge 打开时，默认 Redis 滑块与挑战门面须一起装配。
 */
class PlatformBffChallengeAssemblyTest {

    @Test
    void assemblesDefaultChallengeWhenRedisPresent() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PlatformBffAutoConfiguration.class))
                .withPropertyValues(
                        "platform.security.bff.enabled=true",
                        "platform.security.bff.challenge.enabled=true",
                        "platform.security.bff.challenge.redis-key-prefix=jwy:auth:web-")
                .withBean(UserTokenIssuer.class, StubTokenIssuer::new)
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(RedisSliderCaptchaService.class)
                        .hasSingleBean(LoginChallenge.class)
                        .hasSingleBean(BffChallengeController.class));
    }

    private static final class StubTokenIssuer implements UserTokenIssuer {
        @Override
        public Map<String, Object> issueForAccount(String clientId, cn.miniants.platform.security.account.UserAccount account) {
            return Map.of();
        }

        @Override
        public Map<String, Object> issuePassword(String clientId, String username, String password) {
            return Map.of();
        }

        @Override
        public Map<String, Object> issuePassword(
                String clientId, String username, String password, String designatedUsername) {
            return Map.of();
        }

        @Override
        public Map<String, Object> issueRefresh(String clientId, String refreshToken) {
            return Map.of();
        }
    }
}
