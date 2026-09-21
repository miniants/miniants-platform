package cn.miniants.platform.security.sas;

import cn.miniants.platform.security.JwtCurrentUserFilter;
import cn.miniants.platform.security.SecurityAuditSink;
import cn.miniants.platform.security.account.UserAccountService;
import cn.miniants.platform.security.bff.PublicTokenGrantFilter;
import cn.miniants.platform.security.client.OauthClientLookup;
import cn.miniants.platform.security.sas.keystore.ClasspathSingleKeyStore;
import cn.miniants.platform.security.sas.keystore.JwtKeyStore;
import cn.miniants.platform.security.sas.keystore.RotatableJwkSource;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.security.oauth2.server.authorization.token.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;

import javax.sql.DataSource;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Map;
import java.util.UUID;

/*
 * 必须先于 Boot 的三组安全默认装配注册。否则 Boot 可能先生成临时 JWK、默认用户或默认
 * Authorization Server 安全链，使平台的可轮换密钥和 BFF/SAS 契约失效。
 */
@AutoConfiguration(beforeName = {
        "org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerAutoConfiguration",
        "org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerJwtAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration"
})
@ConditionalOnClass(name = "org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer")
@ConditionalOnProperty(prefix = "platform.security.sas", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(PlatformSasProperties.class)
@Import(PlatformSasAutoConfiguration.InMemoryStoresConfiguration.class)
public class PlatformSasAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder passwordEncoder() {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
        return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", bcrypt));
    }

    @Bean
    @ConditionalOnMissingBean
    public RegisteredClientRepository registeredClientRepository(OauthClientLookup lookup) {
        return new PlatformRegisteredClientRepository(lookup);
    }

    @Bean
    @ConditionalOnMissingBean
    public OAuth2AuthorizationService authorizationService(
            PlatformSasProperties properties,
            ObjectProvider<JdbcOperations> jdbcOperations,
            RegisteredClientRepository registeredClientRepository) {
        JdbcOperations jdbc = jdbcOperations.getIfAvailable();
        if (jdbc != null) {
            JsonMapper jsonMapper = PlatformSasJsonMapper.create();
            JdbcOAuth2AuthorizationService service =
                    new JdbcOAuth2AuthorizationService(jdbc, registeredClientRepository);
            service.setAuthorizationRowMapper(
                    new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationRowMapper(
                            registeredClientRepository, jsonMapper));
            service.setAuthorizationParametersMapper(
                    new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationParametersMapper(jsonMapper));
            return service;
        }
        if (properties.isAllowEphemeralKeys()) {
            return new InMemoryOAuth2AuthorizationService();
        }
        throw new IllegalStateException(
                "platform.security.sas 已启用但缺少 DataSource 且未配置 allow-ephemeral-keys=true");
    }

    @Bean
    @ConditionalOnMissingBean
    public KeyPair keyPair(PlatformSasProperties properties, ResourceLoader resourceLoader) {
        PlatformSasProperties.Keystore keystore = properties.getKeystore();
        if (keystore.isConfigured()) {
            keystore.requireComplete();
            return JwtKeyStores.load(
                    resourceLoader.getResource(keystore.getLocation().trim()),
                    keystore.getStorePassword(),
                    keystore.getAlias().trim(),
                    keystore.getKeyPassword());
        }
        if (!properties.isAllowEphemeralKeys()) {
            throw new IllegalStateException(
                    "platform.security.sas 已启用但未配置 keystore；生产须配置 keystore 或显式 allow-ephemeral-keys=true（仅 demo）");
        }
        return generateRsaKeyPair();
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtKeyStore jwtKeyStore(
            KeyPair keyPair, PlatformSasProperties properties, ResourceLoader resourceLoader) {
        if (properties.getKeystore().isConfigured()) {
            return new ClasspathSingleKeyStore(properties, resourceLoader);
        }
        return new ClasspathSingleKeyStore(keyPair, UUID.randomUUID().toString());
    }

    /**
     * 可热替换 JWKSource。返回 {@link RotatableJwkSource} 以便 admin 注入后 {@code reload()}。
     * 已有任意 {@link JWKSource} 时不注册，避免与自定义源冲突。
     */
    @Bean
    @ConditionalOnMissingBean(JWKSource.class)
    public RotatableJwkSource jwkSource(
            JwtKeyStore jwtKeyStore, KeyPair keyPair, PlatformSasProperties properties) {
        JwtKeyStore fallback = jwtKeyStore instanceof ClasspathSingleKeyStore
                ? jwtKeyStore
                : new ClasspathSingleKeyStore(keyPair, defaultSigningKid(properties));
        return new RotatableJwkSource(jwtKeyStore, fallback);
    }

    @Bean
    @ConditionalOnMissingBean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer() {
        return new PlatformJwtCustomizer();
    }

    @Bean
    @ConditionalOnMissingBean
    public OAuth2TokenGenerator<?> tokenGenerator(JWKSource<SecurityContext> jwkSource,
                                                  OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(jwkSource);
        encoder.setJwkSelector(RotatableJwkSource::selectSigningJwk);
        JwtGenerator jwtGenerator = new JwtGenerator(encoder);
        jwtGenerator.setJwtCustomizer(jwtCustomizer);
        return new DelegatingOAuth2TokenGenerator(
                jwtGenerator, new OAuth2AccessTokenGenerator(), new OAuth2RefreshTokenGenerator());
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthorizationServerSettings authorizationServerSettings(PlatformSasProperties properties) {
        return AuthorizationServerSettings.builder()
                .issuer(properties.getIssuer())
                .tokenEndpoint(properties.getTokenEndpoint())
                .jwkSetEndpoint(properties.getJwkSetEndpoint())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public PasswordGrantAuthenticationProvider passwordGrantAuthenticationProvider(
            UserAccountService userAccountService,
            PasswordEncoder passwordEncoder,
            OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<?> tokenGenerator) {
        return new PasswordGrantAuthenticationProvider(
                userAccountService, passwordEncoder, authorizationService, tokenGenerator);
    }

    @Bean
    public FilterRegistrationBean<PublicTokenGrantFilter> publicTokenGrantFilter(
            PlatformSasProperties properties,
            ObjectProvider<SecurityAuditSink> auditSink) {
        FilterRegistrationBean<PublicTokenGrantFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new PublicTokenGrantFilter(properties, auditSink.getIfAvailable()));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        String endpoint = properties.getTokenEndpoint();
        bean.addUrlPatterns(endpoint.startsWith("/") ? endpoint : "/" + endpoint);
        return bean;
    }

    @Bean
    @ConditionalOnMissingBean(name = "jwtCurrentUserFilter")
    public FilterRegistrationBean<JwtCurrentUserFilter> jwtCurrentUserFilter(JwtDecoder jwtDecoder) {
        FilterRegistrationBean<JwtCurrentUserFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new JwtCurrentUserFilter(jwtDecoder));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 21);
        bean.addUrlPatterns("/*");
        return bean;
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            PasswordGrantAuthenticationProvider passwordGrantAuthenticationProvider,
            ObjectProvider<AdditionalAuthorizationGrant> additionalGrants) throws Exception {
        java.util.List<AdditionalAuthorizationGrant> extras = additionalGrants.orderedStream().toList();
        OAuth2AuthorizationServerConfigurer configurer = new OAuth2AuthorizationServerConfigurer();
        configurer.tokenEndpoint(token -> token
                .accessTokenRequestConverters(converters -> {
                    extras.forEach(grant -> converters.add(0, grant.converter()));
                    converters.add(0, new PasswordGrantAuthenticationConverter());
                    converters.add(0, new ClientCredentialsDefaultScopesConverter());
                })
                .authenticationProviders(providers -> {
                    extras.forEach(grant -> providers.add(0, grant.provider()));
                    providers.add(0, passwordGrantAuthenticationProvider);
                }));
        RequestMatcher endpoints = configurer.getEndpointsMatcher();
        http.securityMatcher(endpoints)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers(endpoints))
                .with(configurer, as -> {
                });
        return http.build();
    }

    static String defaultSigningKid(PlatformSasProperties properties) {
        PlatformSasProperties.Keystore keystore = properties.getKeystore();
        if (keystore.isConfigured() && keystore.getAlias() != null && !keystore.getAlias().isBlank()) {
            return keystore.getAlias().trim();
        }
        return UUID.randomUUID().toString();
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("无法生成 JWT 签名密钥", ex);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnBean(javax.sql.DataSource.class)
    static class JdbcAuthorizationBridge {
        @Bean
        @ConditionalOnMissingBean(JdbcOperations.class)
        JdbcOperations platformSasJdbcOperations(javax.sql.DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingBean(DataSource.class)
    static class InMemoryStoresConfiguration {

        @Bean
        @ConditionalOnMissingBean(UserAccountService.class)
        UserAccountService userAccountService() {
            return new cn.miniants.platform.security.account.InMemoryUserAccountService();
        }

        @Bean
        @ConditionalOnMissingBean(OauthClientLookup.class)
        OauthClientLookup oauthClientLookup() {
            return new cn.miniants.platform.security.client.InMemoryOauthClientLookup();
        }
    }
}
