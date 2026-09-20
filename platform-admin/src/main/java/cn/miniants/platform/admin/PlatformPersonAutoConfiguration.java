package cn.miniants.platform.admin;

import cn.miniants.platform.admin.person.JdbcExternalIdentityBinding;
import cn.miniants.platform.admin.person.JdbcPersonRegistry;
import cn.miniants.platform.admin.person.JdbcPrincipalDirectory;
import cn.miniants.platform.data.PlatformDataProperties;
import cn.miniants.platform.security.PlatformSecurityProperties;
import cn.miniants.platform.security.identity.ExternalIdentityBinding;
import cn.miniants.platform.security.person.PersonRegistry;
import cn.miniants.platform.security.person.PrincipalDirectory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 自然人 / 外部身份 JDBC 默认实现。仅在 {@code platform.security.person.enabled=true} 时装配；
 * 应用可自行提供 Bean 抢 {@code @ConditionalOnMissingBean}。
 */
@AutoConfiguration(
        afterName = {
                "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                "cn.miniants.platform.admin.PlatformAdminAutoConfiguration"
        },
        beforeName = "cn.miniants.platform.security.PlatformSecurityAutoConfiguration"
)
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "platform.security.person", name = "enabled", havingValue = "true")
@EnableConfigurationProperties({PlatformDataProperties.class, PlatformSecurityProperties.class})
public class PlatformPersonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PersonRegistry.class)
    public PersonRegistry personRegistry(JdbcTemplate jdbcTemplate, PlatformDataProperties properties) {
        return new JdbcPersonRegistry(jdbcTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean(PrincipalDirectory.class)
    public PrincipalDirectory principalDirectory(JdbcTemplate jdbcTemplate, PlatformDataProperties properties) {
        return new JdbcPrincipalDirectory(jdbcTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean(ExternalIdentityBinding.class)
    public ExternalIdentityBinding externalIdentityBinding(
            JdbcTemplate jdbcTemplate, PlatformDataProperties properties) {
        return new JdbcExternalIdentityBinding(jdbcTemplate, properties);
    }
}
