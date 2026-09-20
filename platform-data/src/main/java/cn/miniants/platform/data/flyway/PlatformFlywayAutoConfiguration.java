package cn.miniants.platform.data.flyway;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.util.List;

/**
 * 按 {@code platform.flyway.*} 迁业务库。独立装配，不并进
 * {@link cn.miniants.platform.data.PlatformDataAutoConfiguration}。
 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnClass(Flyway.class)
@ConditionalOnProperty(prefix = "platform.flyway", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(PlatformFlywayProperties.class)
public class PlatformFlywayAutoConfiguration {

    @Bean(name = "platformFlyway", initMethod = "migrate")
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(name = "platformFlyway")
    public Flyway platformFlyway(DataSource dataSource, PlatformFlywayProperties properties) {
        List<String> locations = properties.getLocations();
        if (locations == null || locations.isEmpty()) {
            throw new IllegalStateException("platform.flyway.locations 不能为空");
        }
        return Flyway.configure()
                .dataSource(PlatformFlywayDataSources.resolve(dataSource, properties.getDatasource()))
                .table(properties.getTable())
                .locations(locations.toArray(String[]::new))
                .baselineOnMigrate(properties.isBaselineOnMigrate())
                .baselineVersion(properties.getBaselineVersion())
                .baselineDescription(properties.getBaselineDescription())
                .load();
    }
}
