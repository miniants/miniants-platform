package cn.miniants.platform.demo;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "platform.security.enforcement=enforce")
class PlatformDemoMeTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("DELETE FROM sys_oper_log");
        jdbcTemplate.update("DELETE FROM sys_user_role");
        jdbcTemplate.update("DELETE FROM sys_role_resource");
        jdbcTemplate.update("DELETE FROM sys_resource");
        jdbcTemplate.update("DELETE FROM sys_role");
        jdbcTemplate.update("DELETE FROM sys_user");
        jdbcTemplate.update("DELETE FROM sys_oauth_client");
        jdbcTemplate.update("""
                INSERT INTO sys_role (id, code, name, status, version)
                VALUES (10, 'operator', '操作员', 1, 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, username, password, display_name, status, sys_admin, version)
                VALUES (1, 'alice', ?, 'Alice', 1, 0, 1)
                """, passwordEncoder.encode("password"));
        jdbcTemplate.update("INSERT INTO sys_user_role (id, user_id, role_id) VALUES (100, 1, 10)");
        jdbcTemplate.update("""
                INSERT INTO sys_resource (id, code, name, type, sort_no, status, version)
                VALUES (20, 'demo:ping', '演示', 2, 0, 1, 1)
                """);
        jdbcTemplate.update("INSERT INTO sys_role_resource (id, role_id, resource_id) VALUES (300, 10, 20)");
        jdbcTemplate.update("""
                INSERT INTO sys_oauth_client
                (id, client_id, client_secret, client_name, grant_types, scopes,
                 access_token_ttl, refresh_token_ttl, status, version)
                VALUES (1, 'platform-demo', ?, 'Demo', 'client_credentials,password,refresh_token', 'demo',
                        3600, 86400, 1, 1)
                """, passwordEncoder.encode("demo-secret"));
    }

    @Test
    void loginTokenReturnsMeAndWritesOperLog() throws Exception {
        String token = login("alice", "password", 200);
        mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actor").value("user"))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.username").value("alice"))
                .andExpect(jsonPath("$.data.name").value("Alice"))
                .andExpect(jsonPath("$.data.sysAdmin").value(false))
                .andExpect(jsonPath("$.data.roleIds[0]").value(10))
                .andExpect(jsonPath("$.data.permissions[0]").value("demo:ping"))
                .andExpect(jsonPath("$.data.menus[0].code").value("demo:ping"));
        assertEquals(1, countLogs(1));
    }

    @Test
    void changePasswordThenLoginWithNew() throws Exception {
        String token = login("alice", "password", 200);
        mockMvc.perform(post("/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"password\",\"newPassword\":\"changed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
        login("alice", "password", -1);
        login("alice", "changed", 200);
        Integer logs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_oper_log WHERE event_type = 'password_change' AND success = 1",
                Integer.class);
        assertEquals(1, logs);
        String error = jdbcTemplate.queryForObject(
                "SELECT error_message FROM sys_oper_log WHERE event_type = 'password_change'",
                String.class);
        assertTrue(error == null || !error.contains("changed"));
    }

    @Test
    void anonymousMeIsUnauthorized() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未登录"));
    }

    @Test
    void wrongPasswordWritesFailedLogin() throws Exception {
        login("alice", "bad", -1);
        assertEquals(1, countLogs(0));
        assertEquals("alice/platform-demo", jdbcTemplate.queryForObject(
                "SELECT operator_name FROM sys_oper_log WHERE event_type = 'login' AND success = 0",
                String.class));
    }

    private String login(String username, String password, int expectedCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/open/web/password")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return data.path("access_token").asString();
    }

    private int countLogs(int success) {
        String title = success == 1 ? "登录成功" : "登录失败";
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_oper_log WHERE title = ? AND event_type = 'login' AND success = ?",
                Integer.class,
                title, success);
        return count == null ? 0 : count;
    }
}
