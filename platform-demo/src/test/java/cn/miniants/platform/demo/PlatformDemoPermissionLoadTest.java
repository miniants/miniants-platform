package cn.miniants.platform.demo;

import cn.miniants.platform.security.HeaderCurrentUserFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "platform.security.enforcement=enforce")
class PlatformDemoPermissionLoadTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("DELETE FROM sys_user_role");
        jdbcTemplate.update("DELETE FROM sys_role_resource");
        jdbcTemplate.update("DELETE FROM sys_resource");
        jdbcTemplate.update("DELETE FROM sys_role");
        jdbcTemplate.update("DELETE FROM sys_user");
        jdbcTemplate.update("DELETE FROM sys_tenant");
        jdbcTemplate.update("""
                INSERT INTO sys_tenant (id, code, name, status, version)
                VALUES (0, 'default', '默认租户', 1, 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO sys_role (id, code, name, status, version)
                VALUES (10, 'operator', '操作员', 1, 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, username, password, display_name, status, sys_admin, version)
                VALUES (2, 'bob', '{bcrypt}x', 'Bob', 1, 0, 1),
                       (3, 'eve', '{bcrypt}x', 'Eve', 1, 0, 1)
                """);
        jdbcTemplate.update("INSERT INTO sys_user_role (id, user_id, role_id) VALUES (200, 2, 10)");
        jdbcTemplate.update("""
                INSERT INTO sys_resource (id, code, name, type, sort_no, status, version)
                VALUES (20, 'platform:tenant:page', '租户查询', 2, 0, 1, 1)
                """);
        jdbcTemplate.update("INSERT INTO sys_role_resource (id, role_id, resource_id) VALUES (300, 10, 20)");
    }

    @Test
    void loadsCodesFromRoleResourceWhenHeaderOmitsPerms() throws Exception {
        mockMvc.perform(get("/platform/admin/tenant/page")
                        .header(HeaderCurrentUserFilter.USER, "2:bob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].code").value("default"));
    }

    @Test
    void rejectsUserWithoutBoundResource() throws Exception {
        mockMvc.perform(get("/platform/admin/tenant/page")
                        .header(HeaderCurrentUserFilter.USER, "3:eve"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("权限不足"));
    }
}
