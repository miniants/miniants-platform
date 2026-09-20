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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PlatformDemoSasTest {

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
    void clientCredentialsIssuesJwt() throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth/token")
                        .with(httpBasic("platform-demo", "demo-secret"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andReturn();
        JsonNode token = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode claims = payload(token.get("access_token").asString());
        String subject = claims.path("sub").asString("");
        assertTrue("platform-demo".equals(subject) || "1".equals(subject), claims.toString());
        String scope = claims.path("scope").isArray()
                ? claims.get("scope").toString()
                : claims.path("scope").asString("");
        assertTrue(scope.contains("demo"), claims.toString());
    }

    @Test
    void clientCredentialsWithoutScopeParamUsesRegistered() throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth/token")
                        .with(httpBasic("platform-demo", "demo-secret"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode token = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode claims = payload(token.get("access_token").asString());
        String scope = claims.path("scope").isArray()
                ? claims.get("scope").toString()
                : claims.path("scope").asString("");
        assertTrue(scope.contains("demo"), claims.toString());
    }

    @Test
    void publicTokenRejectsPasswordGrant() throws Exception {
        mockMvc.perform(post("/oauth/token")
                        .with(httpBasic("platform-demo", "demo-secret"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("username", "alice")
                        .param("password", "password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unauthorized_client"))
                .andExpect(jsonPath("$.error_description").value("请使用登录门面换票"));
    }

    /** 项目往 {@code public-denied-grants} 里加自己的 grant，公开口就该拦住——内核不预置这些名字。 */
    @Test
    void publicTokenRejectsApplicationDeclaredGrant() throws Exception {
        mockMvc.perform(post("/oauth/token")
                        .with(httpBasic("platform-demo", "demo-secret"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "external")
                        .param("code", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unauthorized_client"));
    }

    @Test
    void jwksIsPublishedAtLegacyPath() throws Exception {
        mockMvc.perform(get("/rsa/publicKey"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"));
    }

    private JsonNode payload(String jwt) throws Exception {
        String payload = jwt.split("\\.")[1];
        byte[] decoded = Base64.getUrlDecoder().decode(payload);
        return objectMapper.readTree(new String(decoded, StandardCharsets.UTF_8));
    }
}
