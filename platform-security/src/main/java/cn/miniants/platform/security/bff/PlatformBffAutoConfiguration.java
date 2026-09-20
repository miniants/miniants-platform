package cn.miniants.platform.security.bff;

import cn.miniants.platform.security.PlatformSecurityProperties;
import cn.miniants.platform.security.SecurityAuditSink;
import cn.miniants.platform.security.account.UserAccountService;
import cn.miniants.platform.security.challenge.LoginChallenge;
import cn.miniants.platform.security.challenge.RedisSliderCaptchaService;
import cn.miniants.platform.security.challenge.RedisSliderLoginChallenge;
import cn.miniants.platform.security.crypto.IdCredentialHasher;
import cn.miniants.platform.security.identity.ExternalIdentityBinding;
import cn.miniants.platform.security.identity.ExternalIdentityProvider;
import cn.miniants.platform.security.identity.ExternalIdentityProviderRegistry;
import cn.miniants.platform.security.person.PersonRegistry;
import cn.miniants.platform.security.person.PrincipalDirectory;
import cn.miniants.platform.security.qr.QrCodeRenderer;
import cn.miniants.platform.security.qr.QrLoginStore;
import cn.miniants.platform.security.qr.RedisQrLoginStore;
import cn.miniants.platform.security.sas.PasswordGrantAuthenticationProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

@AutoConfiguration(afterName = {
        "cn.miniants.platform.security.sas.PlatformSasAutoConfiguration",
        "cn.miniants.platform.admin.PlatformPersonAutoConfiguration",
        "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration"
})
@ConditionalOnClass(name = "org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository")
@ConditionalOnProperty(prefix = "platform.security.bff", name = "enabled", havingValue = "true")
@EnableConfigurationProperties({PlatformBffProperties.class, PlatformSecurityProperties.class})
@Import(BffWebController.class)
public class PlatformBffAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(InternalTokenIssuer.class)
    @ConditionalOnBean({
            RegisteredClientRepository.class,
            PasswordGrantAuthenticationProvider.class,
            OAuth2AuthorizationService.class,
            OAuth2TokenGenerator.class,
            AuthorizationServerSettings.class,
            UserAccountService.class,
            PasswordEncoder.class
    })
    public UserTokenIssuer userTokenIssuer(
            RegisteredClientRepository registeredClientRepository,
            PasswordGrantAuthenticationProvider passwordGrantAuthenticationProvider,
            OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator,
            AuthorizationServerSettings authorizationServerSettings,
            UserAccountService userAccountService,
            PasswordEncoder passwordEncoder,
            ObjectProvider<PrincipalDirectory> principalDirectory,
            PlatformSecurityProperties securityProperties,
            ObjectProvider<SecurityAuditSink> auditSink) {
        return new DefaultInternalTokenIssuer(
                registeredClientRepository,
                passwordGrantAuthenticationProvider,
                authorizationService,
                tokenGenerator,
                authorizationServerSettings,
                userAccountService,
                passwordEncoder,
                principalDirectory,
                securityProperties,
                auditSink);
    }

    @Bean
    @ConditionalOnMissingBean(ExternalIdentityProviderRegistry.class)
    @ConditionalOnBean(ExternalIdentityProvider.class)
    ExternalIdentityProviderRegistry externalIdentityProviderRegistry(
            ObjectProvider<ExternalIdentityProvider> providers) {
        return new ExternalIdentityProviderRegistry(providers.orderedStream().toList());
    }

    @Bean
    public BffFeatureRequirements bffFeatureRequirements(
            PlatformBffProperties properties,
            ObjectProvider<LoginChallenge> loginChallenge,
            ObjectProvider<QrLoginStore> qrLoginStore,
            ObjectProvider<QrCodeRenderer> qrCodeRenderer,
            ObjectProvider<PersonRegistry> personRegistry,
            ObjectProvider<ExternalIdentityBinding> externalIdentityBinding,
            ObjectProvider<ExternalIdentityProvider> externalIdentityProvider,
            ObjectProvider<ExternalIdentityProviderRegistry> externalIdentityProviderRegistry,
            ObjectProvider<IdCredentialHasher> idCredentialHasher) {
        if (properties.getChallenge().isEnabled() && loginChallenge.getIfAvailable() == null) {
            throw new IllegalStateException(
                    "platform.security.bff.challenge.enabled=true 但未找到 LoginChallenge Bean");
        }
        if (properties.getExternal().isEnabled()) {
            if (externalIdentityProviderRegistry.getIfAvailable() == null) {
                throw new IllegalStateException(
                        "platform.security.bff.external.enabled=true 但未找到 ExternalIdentityProvider");
            }
        }
        if (properties.getQr().isEnabled()) {
            if (qrLoginStore.getIfAvailable() == null) {
                throw new IllegalStateException(
                        "platform.security.bff.qr.enabled=true 但未找到 QrLoginStore Bean");
            }
            if (qrCodeRenderer.getIfAvailable() == null) {
                throw new IllegalStateException(
                        "platform.security.bff.qr.enabled=true 但未找到 QrCodeRenderer Bean");
            }
            properties.getQr().requireConfirmProvider();
        }
        if (properties.getRealName().isEnabled()) {
            if (personRegistry.getIfAvailable() == null
                    || externalIdentityBinding.getIfAvailable() == null
                    || externalIdentityProviderRegistry.getIfAvailable() == null
                    || idCredentialHasher.getIfAvailable() == null) {
                throw new IllegalStateException(
                        "platform.security.bff.real-name.enabled=true 但缺少 PersonRegistry / Binding / Provider / Hasher");
            }
        }
        return new BffFeatureRequirements();
    }

    public static final class BffFeatureRequirements {
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "platform.security.bff.qr", name = "enabled", havingValue = "true")
    @Import({BffQrController.class, BffQrConfirmController.class})
    static class QrConfiguration {

        @Bean
        @ConditionalOnMissingBean(QrTokenFacade.class)
        QrTokenFacade qrTokenFacade(
                QrLoginStore qrLoginStore,
                ExternalIdentityBinding binding,
                UserAccountService userAccountService,
                ObjectProvider<PrincipalDirectory> principalDirectory,
                UserTokenIssuer tokenIssuer,
                ObjectProvider<SecurityAuditSink> auditSink) {
            return new QrTokenFacade(
                    qrLoginStore, binding, userAccountService, principalDirectory, tokenIssuer, auditSink);
        }

        @Bean
        @ConditionalOnMissingBean(QrLoginSseHub.class)
        QrLoginSseHub qrLoginSseHub(
                QrLoginStore qrLoginStore,
                QrTokenFacade qrTokenFacade,
                TaskScheduler qrLoginSseTaskScheduler) {
            return new QrLoginSseHub(qrLoginStore, qrTokenFacade, qrLoginSseTaskScheduler);
        }

        @Bean
        @ConditionalOnMissingBean(name = "qrLoginSseTaskScheduler")
        TaskScheduler qrLoginSseTaskScheduler() {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(1);
            scheduler.setThreadNamePrefix("qr-sse-");
            scheduler.initialize();
            return scheduler;
        }

        @Bean
        @ConditionalOnMissingBean(QrLoginStore.class)
        @ConditionalOnClass(StringRedisTemplate.class)
        @ConditionalOnBean(StringRedisTemplate.class)
        QrLoginStore redisQrLoginStore(StringRedisTemplate redis, PlatformBffProperties properties) {
            return new RedisQrLoginStore(redis, properties.getQr().getRedisKeyPrefix());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "platform.security.bff.challenge", name = "enabled", havingValue = "true")
    @Import(BffChallengeController.class)
    static class ChallengeConfiguration {

        @Bean
        @ConditionalOnMissingBean(RedisSliderCaptchaService.class)
        @ConditionalOnClass(StringRedisTemplate.class)
        @ConditionalOnBean(StringRedisTemplate.class)
        RedisSliderCaptchaService redisSliderCaptchaService(
                StringRedisTemplate redis, PlatformBffProperties properties) {
            return new RedisSliderCaptchaService(redis, properties.getChallenge().getRedisKeyPrefix());
        }

        @Bean
        @ConditionalOnMissingBean(LoginChallenge.class)
        LoginChallenge redisSliderLoginChallenge(RedisSliderCaptchaService captchaService) {
            return new RedisSliderLoginChallenge(captchaService);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "platform.security.bff.real-name", name = "enabled", havingValue = "true")
    @Import(BffRealNameController.class)
    static class RealNameConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "platform.security.bff.external", name = "enabled", havingValue = "true")
    @Import(BffExternalController.class)
    static class ExternalConfiguration {

        @Bean
        @ConditionalOnMissingBean
        BffExternalLoginService bffExternalLoginService(
                PlatformBffProperties bffProperties,
                ExternalIdentityProviderRegistry providerRegistry,
                ExternalIdentityBinding binding,
                UserAccountService userAccountService,
                ObjectProvider<PrincipalDirectory> principalDirectory,
                UserTokenIssuer tokenIssuer,
                ObjectProvider<SecurityAuditSink> auditSink) {
            return new BffExternalLoginService(
                    bffProperties,
                    providerRegistry,
                    binding,
                    userAccountService,
                    principalDirectory,
                    tokenIssuer,
                    auditSink);
        }
    }
}
