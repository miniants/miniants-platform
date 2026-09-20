package cn.miniants.platform.admin;

import cn.miniants.platform.admin.config.ConfigRedisCache;
import cn.miniants.platform.admin.config.ConfigSource;
import cn.miniants.platform.admin.config.DefaultConfigSource;
import cn.miniants.platform.admin.config.StringRedisConfigRedisCache;
import cn.miniants.platform.admin.account.AdminCurrentUserMenuLoader;
import cn.miniants.platform.admin.account.JdbcPermissionLoader;
import cn.miniants.platform.admin.account.JdbcUserAccountService;
import cn.miniants.platform.admin.client.JdbcOauthClientLookup;
import cn.miniants.platform.admin.keystore.AesGcmPrivateKeyWrap;
import cn.miniants.platform.admin.keystore.JdbcJwtKeyStore;
import cn.miniants.platform.admin.keystore.JwtWrapKeyResolver;
import cn.miniants.platform.admin.keystore.PlatformJwtKeystoreProperties;
import cn.miniants.platform.admin.mapper.*;
import cn.miniants.platform.admin.oauth.OauthClientChangeListener;
import cn.miniants.platform.admin.seed.AdminSeedRunner;
import cn.miniants.platform.admin.seed.PlatformAdminSeedProperties;
import cn.miniants.platform.admin.service.*;
import cn.miniants.platform.admin.support.AdminOperLogRecorder;
import cn.miniants.platform.admin.support.AdminSecurityAuditSink;
import cn.miniants.platform.admin.web.*;
import cn.miniants.platform.data.PlatformDataProperties;
import cn.miniants.platform.data.audit.AuditorSupplier;
import cn.miniants.platform.security.*;
import cn.miniants.platform.security.account.UserAccountService;
import cn.miniants.platform.security.client.BuiltinFallbackOauthClientLookup;
import cn.miniants.platform.security.client.CachingOauthClientLookup;
import cn.miniants.platform.security.client.OauthClientBuiltinSource;
import cn.miniants.platform.security.client.OauthClientCache;
import cn.miniants.platform.security.client.OauthClientLookup;
import cn.miniants.platform.security.client.RedisOauthClientCache;
import cn.miniants.platform.security.sas.PlatformSasProperties;
import cn.miniants.platform.security.sas.keystore.JwtKeyStore;
import cn.miniants.platform.integration.cache.InvalidationBus;
import cn.miniants.platform.security.sas.keystore.RotatableJwkSource;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;
import java.security.KeyPair;
import java.util.List;
import java.util.Map;

@AutoConfiguration(
        afterName = {
                "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                "cn.miniants.platform.data.flyway.PlatformFlywayAutoConfiguration"
        },
        beforeName = {
                "cn.miniants.platform.data.PlatformDataAutoConfiguration",
                "cn.miniants.platform.security.sas.PlatformSasAutoConfiguration"
        }
)
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnBean(DataSource.class)
@EnableConfigurationProperties({
        PlatformDataProperties.class,
        PlatformAdminApiProperties.class,
        PlatformAdminSeedProperties.class,
        PlatformSecurityProperties.class,
        PlatformJwtKeystoreProperties.class,
        PlatformAdminConfigProperties.class})
@MapperScan("cn.miniants.platform.admin.mapper")
@Import(PlatformAdminAutoConfiguration.AdminApiConfiguration.class)
public class PlatformAdminAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean(UserAccountService.class)
    public UserAccountService userAccountService(JdbcTemplate jdbcTemplate, PlatformDataProperties properties) {
        return new JdbcUserAccountService(jdbcTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean(PermissionLoader.class)
    public PermissionLoader permissionLoader(JdbcTemplate jdbcTemplate, PlatformDataProperties properties) {
        return new JdbcPermissionLoader(jdbcTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean(CurrentUserMenuLoader.class)
    public CurrentUserMenuLoader currentUserMenuLoader(ResourceAdminService resourceAdminService) {
        return new AdminCurrentUserMenuLoader(resourceAdminService);
    }

    /**
     * Redis 客户端缓存必须单独成类。Boot 4 评估 {@code @ConditionalOnMissingBean}
     * 会内省本类全部方法签名；把 {@code StringRedisTemplate} 写在主类上，
     * 没有 Redis 的采用方（如 platform-demo）会在装配 {@code jdbcTemplate} 时
     * {@code NoClassDefFoundError}。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(StringRedisTemplate.class)
    static class OauthClientRedisCacheConfiguration {

        @Bean
        @ConditionalOnBean(StringRedisTemplate.class)
        @ConditionalOnMissingBean(OauthClientCache.class)
        public OauthClientCache oauthClientCache(
                StringRedisTemplate redis, PlatformSecurityProperties securityProperties) {
            PlatformSecurityProperties.OauthClientCacheSettings settings = securityProperties.getOauthClientCache();
            return new RedisOauthClientCache(redis, settings.getRedisKeyPrefix(), settings.getTtl());
        }
    }

    @Bean
    @ConditionalOnBean(OauthClientCache.class)
    @ConditionalOnMissingBean(name = "oauthClientCacheEvictListener")
    public OauthClientChangeListener oauthClientCacheEvictListener(OauthClientCache cache) {
        return cache::evict;
    }

    @Bean
    @ConditionalOnMissingBean(OauthClientLookup.class)
    public OauthClientLookup oauthClientLookup(
            JdbcTemplate jdbcTemplate,
            PlatformDataProperties properties,
            ObjectProvider<OauthClientCache> cache,
            List<OauthClientBuiltinSource> builtins) {
        OauthClientLookup jdbc = new JdbcOauthClientLookup(jdbcTemplate, properties);
        OauthClientLookup layered = new BuiltinFallbackOauthClientLookup(jdbc, builtins);
        OauthClientCache clientCache = cache.getIfAvailable();
        return clientCache == null ? layered : new CachingOauthClientLookup(layered, clientCache);
    }

    @Bean
    @ConditionalOnMissingBean(AuditorSupplier.class)
    public AuditorSupplier auditorSupplier() {
        return new CurrentUserAuditorSupplier();
    }

    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    public PasswordEncoder passwordEncoder() {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
        return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", bcrypt));
    }

    @Bean
    @ConditionalOnMissingBean
    public PasswordChangeService passwordChangeService(
            UserAccountService userAccountService, PasswordEncoder passwordEncoder) {
        return new PasswordChangeService(userAccountService, passwordEncoder);
    }

    @Bean
    @ConditionalOnMissingBean
    public PasswordChangeController passwordChangeController(PasswordChangeService passwordChangeService) {
        return new PasswordChangeController(passwordChangeService);
    }

    @Bean
    @ConditionalOnMissingBean(UserAdminService.class)
    public UserAdminService userAdminService(UserMapper userMapper, UserRoleMapper userRoleMapper,
            UserProfileMapper userProfileMapper, PasswordEncoder passwordEncoder, RoleMapper roleMapper) {
        return new DefaultUserAdminService(userMapper, userRoleMapper, userProfileMapper, passwordEncoder, roleMapper);
    }

    @Bean
    @ConditionalOnMissingBean(RoleAdminService.class)
    public RoleAdminService roleAdminService(RoleMapper roleMapper, UserRoleMapper userRoleMapper,
            RoleResourceMapper roleResourceMapper, RoleDataScopeMapper roleDataScopeMapper,
            ResourceMapper resourceMapper) {
        return new DefaultRoleAdminService(roleMapper, userRoleMapper, roleResourceMapper, roleDataScopeMapper,
                resourceMapper);
    }

    @Bean
    @ConditionalOnMissingBean(ResourceAdminService.class)
    public ResourceAdminService resourceAdminService(ResourceMapper resourceMapper,
            RoleResourceMapper roleResourceMapper, ResourceMetaMapper resourceMetaMapper) {
        return new DefaultResourceAdminService(resourceMapper, roleResourceMapper, resourceMetaMapper);
    }

    @Bean
    @ConditionalOnMissingBean(DictAdminService.class)
    public DictAdminService dictAdminService(DictMapper dictMapper) {
        return new DefaultDictAdminService(dictMapper);
    }

    /**
     * 配置 Redis 缓存必须单独成类，理由同 {@link OauthClientRedisCacheConfiguration}。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(StringRedisTemplate.class)
    static class ConfigRedisCacheConfiguration {

        @Bean
        @ConditionalOnBean(StringRedisTemplate.class)
        @ConditionalOnMissingBean(ConfigRedisCache.class)
        public ConfigRedisCache configRedisCache(StringRedisTemplate redis) {
            return new StringRedisConfigRedisCache(redis);
        }
    }

    @Bean
    @ConditionalOnMissingBean(ConfigSource.class)
    public ConfigSource configSource(
            ConfigMapper configMapper,
            ObjectProvider<ConfigRedisCache> redis,
            ObjectProvider<InvalidationBus> invalidateBus,
            PlatformAdminConfigProperties configProperties) {
        return new DefaultConfigSource(
                configMapper,
                redis,
                invalidateBus,
                configProperties.getRedisKeyPrefix());
    }

    @Bean
    @ConditionalOnMissingBean(ConfigAdminService.class)
    public ConfigAdminService configAdminService(ConfigMapper configMapper, ConfigSource configSource) {
        return new DefaultConfigAdminService(configMapper, configSource);
    }

    @Bean
    @ConditionalOnMissingBean(OperLogAdminService.class)
    public OperLogAdminService operLogAdminService(OperLogMapper operLogMapper) {
        return new DefaultOperLogAdminService(operLogMapper);
    }

    @Bean
    @ConditionalOnMissingBean(OperLogRecorder.class)
    public OperLogRecorder operLogRecorder(OperLogAdminService operLogAdminService) {
        return new AdminOperLogRecorder(operLogAdminService);
    }

    @Bean
    @ConditionalOnMissingBean(SecurityAuditSink.class)
    public SecurityAuditSink securityAuditSink(ObjectProvider<OperLogRecorder> operLogRecorder) {
        return new AdminSecurityAuditSink(operLogRecorder);
    }

    @Bean
    @ConditionalOnMissingBean(OauthClientAdminService.class)
    public OauthClientAdminService oauthClientAdminService(
            OauthClientMapper oauthClientMapper, PasswordEncoder passwordEncoder,
            List<OauthClientChangeListener> changeListeners) {
        return new DefaultOauthClientAdminService(oauthClientMapper, passwordEncoder, changeListeners);
    }

    @Bean
    @ConditionalOnMissingBean(TenantAdminService.class)
    public TenantAdminService tenantAdminService(TenantMapper tenantMapper) {
        return new DefaultTenantAdminService(tenantMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtWrapKeyResolver jwtWrapKeyResolver(
            JdbcTemplate jdbcTemplate,
            PlatformDataProperties dataProperties,
            PlatformJwtKeystoreProperties jwtKeystoreProperties) {
        return new JwtWrapKeyResolver(jdbcTemplate, dataProperties, jwtKeystoreProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public AesGcmPrivateKeyWrap aesGcmPrivateKeyWrap(
            PlatformJwtKeystoreProperties jwtKeystoreProperties,
            JwtWrapKeyResolver jwtWrapKeyResolver) {
        return AesGcmPrivateKeyWrap.from(jwtKeystoreProperties, jwtWrapKeyResolver);
    }

    @Bean
    @ConditionalOnMissingBean(JwtKeyStore.class)
    public JwtKeyStore jwtKeyStore(
            JdbcTemplate jdbcTemplate,
            AesGcmPrivateKeyWrap aesGcmPrivateKeyWrap,
            PlatformDataProperties dataProperties,
            ObjectProvider<KeyPair> keyPair,
            ObjectProvider<PlatformSasProperties> sasProperties) {
        return new JdbcJwtKeyStore(
                jdbcTemplate,
                aesGcmPrivateKeyWrap,
                dataProperties,
                keyPair::getIfAvailable,
                sasProperties::getIfAvailable);
    }

    @Bean
    @ConditionalOnMissingBean(JwtKeystoreAdminService.class)
    public JwtKeystoreAdminService jwtKeystoreAdminService(
            JwtKeystoreMapper jwtKeystoreMapper,
            JdbcTemplate jdbcTemplate,
            AesGcmPrivateKeyWrap aesGcmPrivateKeyWrap,
            PlatformDataProperties dataProperties,
            ObjectProvider<PlatformSasProperties> sasProperties,
            ObjectProvider<KeyPair> keyPair,
            ObjectProvider<RotatableJwkSource> jwkSource,
            ApplicationEventPublisher eventPublisher,
            JwtWrapKeyResolver jwtWrapKeyResolver) {
        return new DefaultJwtKeystoreAdminService(
                jwtKeystoreMapper,
                jdbcTemplate,
                aesGcmPrivateKeyWrap,
                dataProperties,
                sasProperties,
                keyPair,
                jwkSource,
                eventPublisher,
                jwtWrapKeyResolver);
    }

    @Bean
    @ConditionalOnMissingBean(AdminSeedRunner.class)
    @ConditionalOnProperty(prefix = "platform.admin.seed", name = "enabled", havingValue = "true")
    public AdminSeedRunner adminSeedRunner(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder,
            PlatformAdminSeedProperties seedProperties, PlatformDataProperties dataProperties) {
        return new AdminSeedRunner(jdbcTemplate, passwordEncoder, seedProperties, dataProperties);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "platform.admin.api", name = "enabled", matchIfMissing = true)
    @Import({
            UserAdminController.class,
            RoleAdminController.class,
            OauthClientAdminController.class,
            TenantAdminController.class,
            ResourceAdminController.class,
            DictAdminController.class,
            ConfigAdminController.class,
            OperLogAdminController.class,
            JwtKeystoreAdminController.class
    })
    static class AdminApiConfiguration {
    }
}
