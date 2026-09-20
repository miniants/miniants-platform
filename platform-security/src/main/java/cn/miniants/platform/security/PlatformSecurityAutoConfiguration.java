package cn.miniants.platform.security;

import cn.miniants.platform.security.crypto.DefaultIdCredentialHasher;
import cn.miniants.platform.security.crypto.IdCredentialHasher;
import cn.miniants.platform.security.identity.ExternalIdentityBinding;
import cn.miniants.platform.security.person.PersonRegistry;
import cn.miniants.platform.security.person.PrincipalDirectory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.io.ResourceLoader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

@AutoConfiguration(afterName = {
        "cn.miniants.platform.admin.PlatformAdminAutoConfiguration",
        "cn.miniants.platform.admin.PlatformPersonAutoConfiguration",
        "cn.miniants.platform.security.sas.PlatformSasAutoConfiguration",
        "org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration"
})
@ConditionalOnProperty(prefix = "platform.security", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PlatformSecurityProperties.class)
@Import({PlatformSecurityAdvice.class, CurrentUserController.class})
public class PlatformSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdCredentialHasher.class)
    public IdCredentialHasher idCredentialHasher(PlatformSecurityProperties properties) {
        return new DefaultIdCredentialHasher(properties.getPerson().getPepper());
    }

    /**
     * 开关打开但缺关键 SPI 时启动失败，不静默降级。
     * 排在 admin JDBC 默认实现之后（本类 after PlatformAdminAutoConfiguration）。
     */
    @Bean
    public PersonSpiRequirements personSpiRequirements(
            PlatformSecurityProperties properties,
            ObjectProvider<PersonRegistry> personRegistry,
            ObjectProvider<PrincipalDirectory> principalDirectory,
            ObjectProvider<ExternalIdentityBinding> externalIdentityBinding) {
        if (properties.getPerson().isEnabled() && personRegistry.getIfAvailable() == null) {
            throw new IllegalStateException(
                    "platform.security.person.enabled=true 但未找到 PersonRegistry Bean");
        }
        if (properties.getAccount().isMultiPrincipal() && principalDirectory.getIfAvailable() == null) {
            throw new IllegalStateException(
                    "platform.security.account.multi-principal=true 但未找到 PrincipalDirectory Bean");
        }
        if (properties.getExternalId().anyProviderEnabled()
                && externalIdentityBinding.getIfAvailable() == null) {
            throw new IllegalStateException(
                    "platform.security.external-id 已启用 provider 但未找到 ExternalIdentityBinding Bean");
        }
        return new PersonSpiRequirements();
    }

    /** 标记 Bean：person / multi-principal / external-id SPI 校验已通过。 */
    public static final class PersonSpiRequirements {
    }

    @Bean
    @ConditionalOnMissingBean
    public EnforcementModeSource enforcementModeSource(PlatformSecurityProperties properties) {
        return () -> EnforcementMode.from(properties.getEnforcement());
    }

    @Bean
    @ConditionalOnMissingBean
    public SecurityAuditSink securityAuditSink() {
        return new RequestSecurityAuditSink();
    }

    @Bean
    @ConditionalOnMissingBean
    public PermissionInterceptor permissionInterceptor(
            PlatformSecurityProperties properties,
            EnforcementModeSource enforcementModeSource,
            SecurityAuditSink auditSink,
            ObjectProvider<PermissionPolicy> permissionPolicy,
            ObjectProvider<AccessDeniedResponder> deniedResponder,
            PublicAccessPaths publicAccessPaths) {
        return new PermissionInterceptor(
                enforcementModeSource,
                auditSink,
                permissionPolicy.getIfAvailable(),
                deniedResponder.getIfAvailable(),
                publicAccessPaths,
                properties::isRejectUnclassified);
    }

    @Bean
    @ConditionalOnBean(OperLogRecorder.class)
    @ConditionalOnMissingBean
    public OperLogInterceptor operLogInterceptor(
            OperLogRecorder operLogRecorder,
            ObjectProvider<OperLogCustomizer> operLogCustomizer) {
        return new OperLogInterceptor(operLogRecorder, operLogCustomizer.getIfAvailable());
    }

    @Bean
    @ConditionalOnProperty(prefix = "platform.security.owned", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "ownedInterceptor")
    public OwnedInterceptor ownedInterceptor(
            EnforcementModeSource enforcementModeSource,
            SecurityAuditSink auditSink,
            ObjectProvider<AccessDeniedResponder> deniedResponder,
            BeanFactory beanFactory,
            PlatformSecurityProperties properties) {
        return new OwnedInterceptor(
                enforcementModeSource,
                auditSink,
                deniedResponder.getIfAvailable(),
                beanFactory,
                properties.getOwned().isAdminBypass());
    }

    @Bean
    @ConditionalOnProperty(prefix = "platform.security.owned", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "ownedAnnotationRegistrar")
    public OwnedAnnotationRegistrar ownedAnnotationRegistrar(
            List<RequestMappingHandlerMapping> handlerMappings,
            BeanFactory beanFactory) {
        return new OwnedAnnotationRegistrar(handlerMappings, beanFactory);
    }

    @Bean
    @ConditionalOnMissingBean(name = "platformSecurityWebMvcConfigurer")
    public WebMvcConfigurer platformSecurityWebMvcConfigurer(
            PermissionInterceptor interceptor,
            ObjectProvider<OperLogInterceptor> operLogInterceptor,
            ObjectProvider<OwnedInterceptor> ownedInterceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                OwnedInterceptor owned = ownedInterceptor.getIfAvailable();
                if (owned != null) {
                    registry.addInterceptor(owned).addPathPatterns("/**");
                }
                registry.addInterceptor(interceptor).addPathPatterns("/**");
                OperLogInterceptor operLog = operLogInterceptor.getIfAvailable();
                if (operLog != null) {
                    registry.addInterceptor(operLog).addPathPatterns("/**");
                }
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public PublicAccessPaths publicAccessPaths(PlatformSecurityProperties properties) {
        PublicAccessPaths paths = new PublicAccessPaths();
        paths.replaceProtocol(properties.resolvedAnonymousPaths());
        return paths;
    }

    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnMissingBean
    public PublicAccessPathRegistrar publicAccessPathRegistrar(
            PublicAccessPaths publicAccessPaths,
            List<RequestMappingHandlerMapping> handlerMappings) {
        return new PublicAccessPathRegistrar(publicAccessPaths, handlerMappings);
    }

    @Bean
    @ConditionalOnBean(PermissionLoader.class)
    @ConditionalOnMissingBean(name = "permissionLoadFilter")
    public FilterRegistrationBean<PermissionLoadFilter> permissionLoadFilter(PermissionLoader permissionLoader) {
        FilterRegistrationBean<PermissionLoadFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new PermissionLoadFilter(permissionLoader));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 22);
        bean.addUrlPatterns("/*");
        return bean;
    }

    /**
     * 请求头主体只用于测试。即使生产误配 {@code header-auth=true}，运行时 classpath
     * 没有 Boot Test 也不会注册这条可伪造身份的入口。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.test.context.SpringBootTest")
    @ConditionalOnProperty(prefix = "platform.security", name = "header-auth", havingValue = "true")
    static class HeaderAuthTestConfiguration {

        @Bean
        FilterRegistrationBean<HeaderCurrentUserFilter> headerCurrentUserFilter() {
            FilterRegistrationBean<HeaderCurrentUserFilter> bean = new FilterRegistrationBean<>();
            bean.setFilter(new HeaderCurrentUserFilter());
            bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
            bean.addUrlPatterns("/*");
            return bean;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.oauth2.jwt.JwtDecoder")
    static class JwtCurrentUserFilterConfiguration {

        @Bean
        @ConditionalOnBean(type = "org.springframework.security.oauth2.jwt.JwtDecoder")
        @ConditionalOnMissingBean(name = "jwtCurrentUserFilter")
        public FilterRegistrationBean<JwtCurrentUserFilter> jwtCurrentUserFilter(
                org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder) {
            FilterRegistrationBean<JwtCurrentUserFilter> bean = new FilterRegistrationBean<>();
            bean.setFilter(new JwtCurrentUserFilter(jwtDecoder));
            bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 21);
            bean.addUrlPatterns("/*");
            return bean;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.oauth2.jwt.NimbusJwtDecoder")
    @ConditionalOnMissingBean(type = "org.springframework.security.oauth2.jwt.JwtDecoder")
    @Conditional(OnResourceJwtKey.class)
    static class ResourceJwtDecoderConfiguration {

        @Bean
        org.springframework.security.oauth2.jwt.JwtDecoder platformJwtDecoder(
                PlatformSecurityProperties properties, ResourceLoader resourceLoader) {
            PlatformSecurityProperties.Jwt jwt = properties.getJwt();
            return RsaJwtDecoders.from(
                    null,
                    jwt.getPublicKey(),
                    jwt.getPublicKeyLocation(),
                    jwt.getJwkJson(),
                    resourceLoader);
        }
    }

    static class OnResourceJwtKey extends AnyNestedCondition {

        OnResourceJwtKey() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(prefix = "platform.security.jwt", name = "public-key")
        static class PublicKey {
        }

        @ConditionalOnProperty(prefix = "platform.security.jwt", name = "public-key-location")
        static class PublicKeyLocation {
        }

        @ConditionalOnProperty(prefix = "platform.security.jwt", name = "jwk-json")
        static class JwkJson {
        }
    }
}
