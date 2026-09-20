package cn.miniants.platform.security.bff;

import cn.miniants.platform.security.account.UserAccountService;
import cn.miniants.platform.security.identity.ExternalIdentity;
import cn.miniants.platform.security.identity.ExternalIdentityBinding;
import cn.miniants.platform.security.qr.QrCodeRenderer;
import cn.miniants.platform.security.qr.QrLoginStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import cn.miniants.platform.security.sas.PlatformGrantTypes;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 扫码门面打开时，QrTokenFacade 必须和 BffQrController 一起装配。
 * 不能用 {@code @ConditionalOnBean(QrLoginStore)}：同配置类里的 redisQrLoginStore 在条件评估时还看不见。
 */
class PlatformBffQrAssemblyTest {

    @Test
    void assemblesQrFacadeWhenStoreComesFromSameConfiguration() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PlatformBffAutoConfiguration.class))
                .withPropertyValues(
                        "platform.security.bff.enabled=true",
                        "platform.security.bff.qr.enabled=true",
                        "platform.security.bff.qr.confirm-provider=WX")
                .withBean(UserTokenIssuer.class, StubTokenIssuer::new)
                .withBean(UserAccountService.class, StubUserAccountService::new)
                .withBean(ExternalIdentityBinding.class, StubBinding::new)
                .withBean(QrCodeRenderer.class, () -> scene -> new byte[0])
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(RegisteredClientRepository.class, () -> new RegisteredClientRepository() {
                    @Override
                    public void save(RegisteredClient registeredClient) {
                    }

                    @Override
                    public RegisteredClient findById(String id) {
                        return null;
                    }

                    @Override
                    public RegisteredClient findByClientId(String clientId) {
                        return RegisteredClient.withId("1")
                                .clientId(clientId)
                                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                                .authorizationGrantType(PlatformGrantTypes.QR)
                                .build();
                    }
                })
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(QrLoginStore.class)
                        .hasSingleBean(QrTokenFacade.class)
                        .hasSingleBean(BffQrController.class)
                        .hasSingleBean(BffQrConfirmController.class)
                        .hasSingleBean(QrLoginSseHub.class));
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

    private static final class StubUserAccountService implements UserAccountService {
        @Override
        public Optional<cn.miniants.platform.security.account.UserAccount> findByUsername(String username) {
            return Optional.empty();
        }
    }

    private static final class StubBinding implements ExternalIdentityBinding {
        @Override
        public Optional<ExternalIdentity> find(String provider, String subject) {
            return Optional.empty();
        }

        @Override
        public Optional<ExternalIdentity> findByPerson(String provider, Long personId) {
            return Optional.empty();
        }

        @Override
        public ExternalIdentity bind(String provider, String subject, Long personId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void reassignPerson(Long fromPersonId, Long toPersonId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean unbindByPerson(String provider, Long personId) {
            throw new UnsupportedOperationException();
        }
    }
}
