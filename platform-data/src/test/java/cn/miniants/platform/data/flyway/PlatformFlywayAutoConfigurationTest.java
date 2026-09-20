package cn.miniants.platform.data.flyway;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformFlywayAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PlatformFlywayAutoConfiguration.class))
            .withUserConfiguration(H2DataSourceConfig.class);

    @Test
    void disabledByDefaultDoesNotRegisterFlyway() {
        runner.run(context -> assertThat(context).doesNotHaveBean("platformFlyway"));
    }

    @Test
    void enabledMigratesAgainstTheInjectedDataSource() {
        runner.withPropertyValues(
                        "platform.flyway.enabled=true",
                        "platform.flyway.locations=classpath:db/platform-flyway-test")
                .run(context -> {
                    assertThat(context).hasBean("platformFlyway");
                    DataSource dataSource = context.getBean(DataSource.class);
                    try (var connection = dataSource.getConnection();
                            var statement = connection.createStatement();
                            var result = statement.executeQuery(
                                    "SELECT COUNT(*) FROM platform_flyway_marker")) {
                        assertThat(result.next()).isTrue();
                        assertThat(result.getInt(1)).isZero();
                    }
                });
    }

    @Test
    void defaultsMatchDocumentedConvention() {
        PlatformFlywayProperties properties = new PlatformFlywayProperties();
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getDatasource()).isEmpty();
        assertThat(properties.getTable()).isEqualTo("flyway_schema_history");
        assertThat(properties.getLocations()).containsExactly("classpath:db/migration");
        assertThat(properties.isBaselineOnMigrate()).isTrue();
        assertThat(properties.getBaselineVersion()).isEqualTo("1");
        assertThat(properties.getBaselineDescription()).isEqualTo("existing schema");
    }

    @Test
    void blankNameKeepsTheInjectedDataSource() {
        DataSource dataSource = mock(DataSource.class);
        assertThat(PlatformFlywayDataSources.resolve(dataSource, "  ")).isSameAs(dataSource);
    }

    @Test
    void namedMasterIsTakenFromDynamicRouting() {
        DataSource master = mock(DataSource.class);
        DynamicRoutingDataSource routing = mock(DynamicRoutingDataSource.class);
        when(routing.getDataSource("master")).thenReturn(master);
        assertThat(PlatformFlywayDataSources.resolve(routing, "master")).isSameAs(master);
    }

    @Test
    void missingNamedSourceFails() {
        DynamicRoutingDataSource routing = mock(DynamicRoutingDataSource.class);
        when(routing.getDataSource("finance")).thenReturn(null);
        assertThatThrownBy(() -> PlatformFlywayDataSources.resolve(routing, "finance"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("finance");
    }

    @Configuration
    static class H2DataSourceConfig {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setUrl("jdbc:h2:mem:platform_flyway_" + UUID.randomUUID()
                    + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            dataSource.setDriverClassName("org.h2.Driver");
            return dataSource;
        }
    }
}
