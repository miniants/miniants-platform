package cn.miniants.platform.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import org.springframework.jdbc.CannotGetJdbcConnectionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(classes = TenantTestApplication.class)
@TestPropertySource(properties = {
        "platform.tenant.strategy=database",
        "platform.tenant.default-tenant=alpha",
        "platform.tenant.database.targets.alpha.url=jdbc:h2:mem:tenant-alpha;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "platform.tenant.database.targets.beta.url=jdbc:h2:mem:tenant-beta;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"
})
class DatabaseTenantTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void tables() {
        createTicket("alpha");
        createTicket("beta");
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void unknownTenantFailsFast() {
        TenantContext.bind("missing");
        CannotGetJdbcConnectionException ex = assertThrows(CannotGetJdbcConnectionException.class,
                () -> jdbcTemplate.queryForObject("SELECT 1", Integer.class));
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertEquals("Cannot determine target DataSource for lookup key [missing]", ex.getCause().getMessage());
    }

    @Test
    void routesToDifferentDatabases() {
        TenantContext.bind("alpha");
        jdbcTemplate.update("INSERT INTO demo_ticket (id, title) VALUES (1, 'A')");
        TenantContext.bind("beta");
        jdbcTemplate.update("INSERT INTO demo_ticket (id, title) VALUES (1, 'B')");

        TenantContext.bind("alpha");
        assertEquals("A", jdbcTemplate.queryForObject("SELECT title FROM demo_ticket WHERE id = 1", String.class));
        TenantContext.bind("beta");
        assertEquals("B", jdbcTemplate.queryForObject("SELECT title FROM demo_ticket WHERE id = 1", String.class));
    }

    private void createTicket(String tenant) {
        TenantContext.bind(tenant);
        jdbcTemplate.update("""
                CREATE TABLE IF NOT EXISTS demo_ticket (
                    id BIGINT NOT NULL PRIMARY KEY,
                    title VARCHAR(100)
                )
                """);
        jdbcTemplate.update("DELETE FROM demo_ticket");
    }
}
