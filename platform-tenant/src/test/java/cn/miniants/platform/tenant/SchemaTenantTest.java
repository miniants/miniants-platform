package cn.miniants.platform.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = TenantTestApplication.class)
@TestPropertySource(properties = {
        "platform.tenant.strategy=schema",
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.minimum-idle=1"
})
class SchemaTenantTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void schemas() {
        jdbcTemplate.update("CREATE SCHEMA IF NOT EXISTS alpha");
        jdbcTemplate.update("CREATE SCHEMA IF NOT EXISTS beta");
        createTicket("alpha");
        createTicket("beta");
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void switchesSchema() {
        TenantContext.bind("alpha");
        jdbcTemplate.update("INSERT INTO demo_ticket (id, title) VALUES (1, 'A')");
        TenantContext.bind("beta");
        jdbcTemplate.update("INSERT INTO demo_ticket (id, title) VALUES (1, 'B')");

        TenantContext.bind("alpha");
        assertEquals("A", jdbcTemplate.queryForObject("SELECT title FROM demo_ticket WHERE id = 1", String.class));
        TenantContext.bind("beta");
        assertEquals("B", jdbcTemplate.queryForObject("SELECT title FROM demo_ticket WHERE id = 1", String.class));
    }

    @Test
    void restoresSchemaBeforePooledConnectionIsReusedWithoutTenant() {
        TenantContext.bind("alpha");
        assertEquals("alpha", jdbcTemplate.queryForObject("SELECT CURRENT_SCHEMA", String.class));

        TenantContext.clear();
        assertEquals("public", jdbcTemplate.queryForObject("SELECT CURRENT_SCHEMA", String.class));
    }

    private void createTicket(String schema) {
        TenantContext.bind(schema);
        jdbcTemplate.update("""
                CREATE TABLE IF NOT EXISTS demo_ticket (
                    id BIGINT NOT NULL PRIMARY KEY,
                    title VARCHAR(100)
                )
                """);
        jdbcTemplate.update("DELETE FROM demo_ticket");
    }
}
