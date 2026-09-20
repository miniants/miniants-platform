package cn.miniants.platform.ratelimit.admin;

import cn.miniants.platform.ratelimit.admin.publish.NoOpRateLimitPolicySnapshotPublisher;
import cn.miniants.platform.ratelimit.admin.publish.RateLimitAdminPublishReconcileScheduler;
import cn.miniants.platform.ratelimit.admin.service.RateLimitPolicyAdminService;
import cn.miniants.platform.ratelimit.admin.snapshot.RateLimitPolicySnapshotCodec;
import cn.miniants.platform.ratelimit.admin.store.RateLimitPolicyJdbcRepository;
import cn.miniants.platform.ratelimit.admin.web.RateLimitAdminAdvice;
import cn.miniants.platform.ratelimit.admin.web.RateLimitAdminUiConfigController;
import cn.miniants.platform.ratelimit.admin.web.RateLimitPolicyAdminController;
import cn.miniants.platform.ratelimit.config.PlatformRateLimitCoreAutoConfiguration;
import cn.miniants.platform.ratelimit.config.RateLimitProperties;
import cn.miniants.platform.ratelimit.metrics.RateLimitMetricsRecorder;
import cn.miniants.platform.ratelimit.policy.CompositeRateLimitPolicyRegistry;
import cn.miniants.platform.ratelimit.spi.RateLimitBucketInspector;
import cn.miniants.platform.ratelimit.redis.snapshot.RateLimitPolicySnapshotPublisher;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;

@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@AutoConfigureAfter({
        DataSourceAutoConfiguration.class,
        PlatformRateLimitCoreAutoConfiguration.class
})
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "platform.ratelimit.admin", name = "enabled", havingValue = "true")
@EnableConfigurationProperties({RateLimitAdminProperties.class, RateLimitProperties.class})
@EnableScheduling
@Import({
        RateLimitPolicyAdminController.class,
        RateLimitAdminAdvice.class,
        RateLimitAdminUiConfigController.class,
        RateLimitAdminRedisPublishConfiguration.class
})
public class PlatformRateLimitAdminAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PlatformRateLimitAdminAutoConfiguration.class);

    @Bean(name = "rateLimitFlyway", initMethod = "migrate")
    @ConditionalOnClass(Flyway.class)
    @ConditionalOnProperty(prefix = "platform.ratelimit.admin.flyway", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public Flyway rateLimitFlyway(DataSource dataSource, RateLimitAdminProperties properties) {
        return RateLimitAdminFlywaySupport.create(dataSource, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitPolicyJdbcRepository rateLimitPolicyJdbcRepository(JdbcTemplate jdbcTemplate) {
        return new RateLimitPolicyJdbcRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitPolicySnapshotCodec rateLimitAdminSnapshotCodec(ObjectProvider<ObjectMapper> objectMapper) {
        return new RateLimitPolicySnapshotCodec(objectMapper.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnMissingBean(RateLimitPolicySnapshotPublisher.class)
    public RateLimitPolicySnapshotPublisher rateLimitPolicySnapshotPublisher(
            RateLimitAdminProperties adminProperties) {
        if (adminProperties.isStandalone()) {
            log.warn("限流管理面以单机模式运行，策略不会同步到其他实例");
            return new NoOpRateLimitPolicySnapshotPublisher();
        }
        throw new IllegalStateException(
                "动态限流管理需要 Redis。多实例请引入 spring-boot-starter-data-redis；"
                        + "单机演示请设置 platform.ratelimit.admin.standalone=true");
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitPolicyAdminService rateLimitPolicyAdminService(
            RateLimitPolicyJdbcRepository repository,
            RateLimitPolicySnapshotCodec adminCodec,
            cn.miniants.platform.ratelimit.snapshot.RateLimitPolicySnapshotCodec runtimeCodec,
            RateLimitPolicySnapshotPublisher publisher,
            CompositeRateLimitPolicyRegistry registry,
            RateLimitProperties rateLimitProperties,
            ObjectProvider<RateLimitMetricsRecorder> metrics,
            ObjectProvider<RateLimitBucketInspector> bucketInspector) {
        return new RateLimitPolicyAdminService(
                repository,
                adminCodec,
                runtimeCodec,
                publisher,
                registry,
                rateLimitProperties,
                metrics.getIfAvailable(() -> RateLimitMetricsRecorder.NOOP),
                bucketInspector.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitAdminPublishReconcileScheduler rateLimitAdminPublishReconcileScheduler(
            RateLimitPolicyAdminService adminService,
            CompositeRateLimitPolicyRegistry registry) {
        return new RateLimitAdminPublishReconcileScheduler(adminService, registry);
    }

    /**
     * 不要用 {@code @ConditionalOnBean(RateLimitPolicyAdminService)}：同轮 AutoConfig
     * 评估时 Service 往往还没注册，条件会静默跳过，现网就不会出现 live:* 键。
     */
    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnProperty(prefix = "platform.ratelimit.admin.live", name = "enabled", havingValue = "true")
    public RateLimitBucketHistory rateLimitBucketHistory(
            StringRedisTemplate redisTemplate,
            RateLimitProperties properties,
            RateLimitAdminProperties adminProperties,
            RateLimitPolicyAdminService adminService) {
        return new RateLimitBucketHistory(redisTemplate, properties, adminProperties, adminService);
    }
}
