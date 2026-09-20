package cn.miniants.platform.ratelimit.admin;

import org.flywaydb.core.Flyway;
import org.h2.Driver;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitAdminFlywaySupportTest {

    @Test
    void migrateCreatesTablesWhenHostSchemaAlreadyHasTables() {
        DataSource dataSource = memoryDb();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE leftover_business (id INT PRIMARY KEY)");

        RateLimitAdminFlywaySupport.create(dataSource, new RateLimitAdminProperties()).migrate();

        assertThat(jdbc.queryForObject("SELECT COUNT(1) FROM sys_rate_limit_policy", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(1) FROM sys_rate_limit_policy_revision", Long.class)).isZero();
    }

    @Test
    void defaultBaselineVersionOneSkipsV1OnNonEmptySchema() {
        DataSource dataSource = memoryDb();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE leftover_business (id INT PRIMARY KEY)");

        Flyway.configure()
                .dataSource(dataSource)
                .table("ratelimit_schema_history")
                .locations("classpath:db/ratelimit-migration-v1-only")
                .baselineOnMigrate(true)
                .load()
                .migrate();

        assertThat(tableCount(jdbc, "sys_rate_limit_policy")).isZero();
    }

    @Test
    void v2RepairsSchemaAfterBogusBaselineOne() {
        DataSource dataSource = memoryDb();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE leftover_business (id INT PRIMARY KEY)");

        Flyway.configure()
                .dataSource(dataSource)
                .table("ratelimit_schema_history")
                .locations("classpath:db/ratelimit-migration-v1-only")
                .baselineOnMigrate(true)
                .load()
                .migrate();

        RateLimitAdminFlywaySupport.create(dataSource, new RateLimitAdminProperties()).migrate();

        assertThat(jdbc.queryForObject("SELECT COUNT(1) FROM sys_rate_limit_policy", Long.class)).isZero();
    }

    private static long tableCount(JdbcTemplate jdbc, String table) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM information_schema.tables WHERE lower(table_name) = ?",
                Long.class,
                table.toLowerCase());
        return count == null ? 0L : count;
    }

    private static DataSource memoryDb() {
        return new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:rl_fw_" + UUID.randomUUID()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
                "sa",
                "");
    }
}
