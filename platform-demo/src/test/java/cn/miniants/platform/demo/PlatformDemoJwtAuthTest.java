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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "platform.security.enforcement=enforce")
class PlatformDemoJwtAuthTest {

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
        jdbcTemplate.update("DELETE FROM sys_user_role");
        jdbcTemplate.update("DELETE FROM sys_role_resource");
        jdbcTemplate.update("DELETE FROM sys_resource");
        jdbcTemplate.update("DELETE FROM sys_role");
        jdbcTemplate.update("DELETE FROM sys_user");
        jdbcTemplate.update("DELETE FROM sys_oauth_client");
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
                VALUES (1, 'alice', ?, 'Alice', 1, 0, 1),
                       (3, 'eve', ?, 'Eve', 1, 0, 1)
                """, passwordEncoder.encode("password"), passwordEncoder.encode("password"));
        jdbcTemplate.update("INSERT INTO sys_user_role (id, user_id, role_id) VALUES (100, 1, 10)");
        jdbcTemplate.update("""
                INSERT INTO sys_resource (id, code, name, type, sort_no, status, version)
                VALUES (20, 'demo:ping', '演示', 2, 0, 1, 1),
                       (21, 'platform:tenant:page', '租户查询', 2, 1, 1, 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO sys_role_resource (id, role_id, resource_id)
                VALUES (300, 10, 20), (301, 10, 21)
                """);
        jdbcTemplate.update("""
                INSERT INTO sys_oauth_client
                (id, client_id, client_secret, client_name, grant_types, scopes,
                 access_token_ttl, refresh_token_ttl, status, version)
                VALUES (1, 'platform-demo', ?, 'Demo', 'client_credentials,password,refresh_token',
                        'demo,demo:ping', 3600, 86400, 1, 1)
                """, passwordEncoder.encode("demo-secret"));
    }

    @Test
    void passwordTokenCanCallGuardedApi() throws Exception {
        String token = login("alice");
        mockMvc.perform(get("/__demo/secure").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("pong"));
        mockMvc.perform(get("/platform/admin/tenant/page")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].code").value("default"));
    }

    @Test
    void passwordTokenWithoutResourceIsForbidden() throws Exception {
        String token = login("eve");
        mockMvc.perform(get("/platform/admin/tenant/page")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("权限不足"));
    }

    @Test
    void clientCredentialsTokenMatchesScope() throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth/token")
                        .with(httpBasic("platform-demo", "demo-secret"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "demo:ping"))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("access_token").asString();
        mockMvc.perform(get("/__demo/secure").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("pong"));
    }

    @Test
    void badBearerIsUnauthenticated() throws Exception {
        mockMvc.perform(get("/__demo/secure").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未登录"));
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/open/web/password")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", username)
                        .param("password", "password"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return data.path("access_token").asString();
    }
}
