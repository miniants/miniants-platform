package cn.miniants.platform.demo;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PlatformDemoBffTest {

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
        jdbcTemplate.update("DELETE FROM sys_role");
        jdbcTemplate.update("DELETE FROM sys_user");
        jdbcTemplate.update("DELETE FROM sys_oauth_client");
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, username, password, display_name, status, sys_admin, version)
                VALUES (1, 'alice', ?, 'Alice', 1, 0, 1)
                """, passwordEncoder.encode("password"));
        jdbcTemplate.update("""
                INSERT INTO sys_role (id, code, name, status, version)
                VALUES (10, 'operator', '操作员', 1, 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO sys_user_role (id, user_id, role_id) VALUES (100, 1, 10)
                """);
        jdbcTemplate.update("""
                INSERT INTO sys_oauth_client
                (id, client_id, client_secret, client_name, grant_types, scopes,
                 access_token_ttl, refresh_token_ttl, status, version)
                VALUES (1, 'platform-demo', ?, 'Demo', 'client_credentials,password,refresh_token', 'demo',
                        3600, 86400, 1, 1)
                """, passwordEncoder.encode("demo-secret"));
    }

    @Test
    void passwordFacadeIssuesJwtWithoutClientSecret() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/open/web/password")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "alice")
                        .param("password", "password"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.access_token").exists())
                .andExpect(jsonPath("$.data.refresh_token").exists())
                .andExpect(jsonPath("$.data.client_secret").doesNotExist())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertFalse(data.toString().contains("demo-secret"), data.toString());
        JsonNode claims = payload(data.path("access_token").asString());
        assertEquals(1L, claims.get("userId").asLong());
        assertEquals("alice", claims.get("username").asString());
        assertEquals("Alice", claims.get("name").asString());
        assertEquals(false, claims.get("sysAdmin").asBoolean());
        assertTrue(claims.get("roleIds").toString().contains("10"));
        assertFalse(claims.has("principals"), claims.toString());
        assertFalse(claims.has("individual"), claims.toString());
    }

    @Test
    void refreshFacadeReissuesAccessToken() throws Exception {
        MvcResult login = mockMvc.perform(post("/auth/open/web/password")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "alice")
                        .param("password", "password"))
                .andExpect(status().isOk())
                .andReturn();
        String refresh = objectMapper.readTree(login.getResponse().getContentAsString())
                .path("data").path("refresh_token").asString();
        MvcResult refreshed = mockMvc.perform(post("/auth/open/web/refresh")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("refresh_token", refresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token").exists())
                .andExpect(jsonPath("$.data.client_secret").doesNotExist())
                .andReturn();
        JsonNode claims = payload(objectMapper.readTree(refreshed.getResponse().getContentAsString())
                .path("data").path("access_token").asString());
        assertEquals(1L, claims.get("userId").asLong());
    }

    @Test
    void wrongPasswordIsChinese() throws Exception {
        mockMvc.perform(post("/auth/open/web/password")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "alice")
                        .param("password", "bad"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    private JsonNode payload(String jwt) throws Exception {
        String encoded = jwt.split("\\.")[1];
        byte[] decoded = Base64.getUrlDecoder().decode(encoded);
        return objectMapper.readTree(new String(decoded, StandardCharsets.UTF_8));
    }
}
