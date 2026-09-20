package cn.miniants.platform.admin.seed;

import cn.miniants.platform.data.PlatformDataProperties;
import org.flywaydb.core.Flyway;
import org.h2.Driver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminSeedRunnerTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void migrate() {
        DataSource dataSource = new SimpleDriverDataSource(new Driver(),
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void freshDatabaseGetsAnAdminThatCanActuallyLogIn() {
        run(seed("admin", "s3cret"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT sys_admin FROM sys_user WHERE username = 'admin'", Integer.class)).isEqualTo(1);
        String hash = jdbcTemplate.queryForObject(
                "SELECT password FROM sys_user WHERE username = 'admin'", String.class);
        assertThat(passwordEncoder.matches("s3cret", hash)).isTrue();
    }

    /** 种子配置留在 yml 里是常态。每次重启都重置口令的话，线上改过的口令会被悄悄改回来。 */
    @Test
    void rerunDoesNotResetAnAlreadyChangedPassword() {
        run(seed("admin", "s3cret"));
        jdbcTemplate.update("UPDATE sys_user SET password = ? WHERE username = 'admin'",
                passwordEncoder.encode("changed-in-production"));

        run(seed("admin", "s3cret"));

        String hash = jdbcTemplate.queryForObject(
                "SELECT password FROM sys_user WHERE username = 'admin'", String.class);
        assertThat(passwordEncoder.matches("changed-in-production", hash)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_user WHERE username = 'admin'", Integer.class)).isEqualTo(1);
    }

    /** 内置默认口令等于每个照抄的项目都带同一个后门，所以宁可起不来。 */
    @Test
    void missingPasswordFailsStartupInsteadOfInventingOne() {
        assertThatThrownBy(() -> run(seed("admin", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("password");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM sys_user", Integer.class)).isZero();
    }

    @Test
    void clientIsSeededOnlyWhenItsIdIsConfigured() {
        PlatformAdminSeedProperties withoutClient = seed("admin", "s3cret");
        run(withoutClient);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM sys_oauth_client", Integer.class)).isZero();

        PlatformAdminSeedProperties withClient = seed("other", "s3cret");
        withClient.getClient().setClientId("console");
        withClient.getClient().setClientSecret("console-secret");
        run(withClient);

        String hash = jdbcTemplate.queryForObject(
                "SELECT client_secret FROM sys_oauth_client WHERE client_id = 'console'", String.class);
        assertThat(passwordEncoder.matches("console-secret", hash)).isTrue();
    }

    @Test
    void clientWithoutSecretFailsInsteadOfCreatingAnOpenClient() {
        PlatformAdminSeedProperties properties = seed("admin", "s3cret");
        properties.getClient().setClientId("console");

        assertThatThrownBy(() -> run(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("client-secret");
    }

    private void run(PlatformAdminSeedProperties properties) {
        new AdminSeedRunner(jdbcTemplate, passwordEncoder, properties, new PlatformDataProperties())
                .run(null);
    }

    private static PlatformAdminSeedProperties seed(String username, String password) {
        PlatformAdminSeedProperties properties = new PlatformAdminSeedProperties();
        properties.setEnabled(true);
        properties.setUsername(username);
        properties.setPassword(password);
        return properties;
    }
}
