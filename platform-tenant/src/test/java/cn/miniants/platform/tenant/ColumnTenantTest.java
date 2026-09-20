package cn.miniants.platform.tenant;

import cn.miniants.platform.core.error.PlatformException;
import cn.miniants.platform.tenant.support.DemoTicket;
import cn.miniants.platform.tenant.support.DemoTicketMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(classes = TenantTestApplication.class)
@TestPropertySource(properties = "platform.tenant.strategy=column")
class ColumnTenantTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DemoTicketMapper demoTicketMapper;

    @BeforeEach
    void table() {
        jdbcTemplate.update("""
                CREATE TABLE IF NOT EXISTS demo_ticket (
                    id BIGINT NOT NULL PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    title VARCHAR(100)
                )
                """);
        jdbcTemplate.update("DELETE FROM demo_ticket");
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void isolatesRowsByTenant() {
        TenantContext.bind("alpha");
        DemoTicket one = new DemoTicket();
        one.setTitle("A");
        demoTicketMapper.insert(one);

        TenantContext.bind("beta");
        DemoTicket two = new DemoTicket();
        two.setTitle("B");
        demoTicketMapper.insert(two);

        TenantContext.bind("alpha");
        assertEquals(1, demoTicketMapper.selectList(null).size());
        assertEquals("A", demoTicketMapper.selectList(null).get(0).getTitle());
        assertEquals("alpha", demoTicketMapper.selectList(null).get(0).getTenantId());

        TenantContext.bind("beta");
        assertEquals(1, demoTicketMapper.selectList(null).size());
        assertEquals("B", demoTicketMapper.selectList(null).get(0).getTitle());
    }

    @Test
    void requiresTenantOnBusinessTable() {
        Exception ex = assertThrows(Exception.class, () -> demoTicketMapper.selectList(null));
        Throwable current = ex;
        while (current != null && !(current instanceof PlatformException)) {
            current = current.getCause();
        }
        assertEquals("未指定租户", current == null ? ex.getMessage() : current.getMessage());
    }
}
