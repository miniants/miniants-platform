package cn.miniants.platform.demo;

import cn.miniants.platform.security.HeaderCurrentUserFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PlatformDemoAdminTest {

    private static final String USER_PERMS = "platform:user:page,platform:user:save,platform:user:delete";
    private static final String ROLE_PERMS = "platform:role:page,platform:role:save,platform:role:delete";
    private static final String CLIENT_PERMS =
            "platform:oauth-client:page,platform:oauth-client:save,platform:oauth-client:delete";
    private static final String TENANT_PERMS =
            "platform:tenant:page,platform:tenant:save,platform:tenant:delete";
    private static final String RESOURCE_PERMS =
            "platform:resource:page,platform:resource:save,platform:resource:delete";
    private static final String DICT_PERMS =
            "platform:dict:page,platform:dict:save,platform:dict:delete";
    private static final String CONFIG_PERMS =
            "platform:config:page,platform:config:save,platform:config:delete";
    private static final String OPER_LOG_PERMS =
            "platform:oper-log:page,platform:oper-log:save,platform:oper-log:delete";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("DELETE FROM sys_user_role");
        jdbcTemplate.update("DELETE FROM sys_role_resource");
        jdbcTemplate.update("DELETE FROM sys_resource");
        jdbcTemplate.update("DELETE FROM sys_dict");
        jdbcTemplate.update("DELETE FROM sys_config");
        jdbcTemplate.update("DELETE FROM sys_oper_log");
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
                VALUES (0, 'root', '{bcrypt}$2a$10$placeholderplaceholderplaceholde', '系统', 1, 1, 1)
                """);
    }

    @Test
    void userCrudHidesPasswordAndUpdatesIdZero() throws Exception {
        MvcResult created = mockMvc.perform(auth(post("/platform/admin/user/save"), USER_PERMS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"bob","password":"secret","displayName":"Bob","roleIds":[10],
                                 "profile":{"realName":"测试用户"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("bob"))
                .andExpect(jsonPath("$.data.roleIds[0]").value(10))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andReturn();
        long userId = objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
        assertTrue(userId != 0L);

        mockMvc.perform(auth(get("/platform/admin/user/" + userId), USER_PERMS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.displayName").value("Bob"));

        mockMvc.perform(auth(post("/platform/admin/user/save"), USER_PERMS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":0,\"displayName\":\"超管\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(0))
                .andExpect(jsonPath("$.data.displayName").value("超管"))
                .andExpect(jsonPath("$.data.username").value("root"));

        mockMvc.perform(auth(get("/platform/admin/user/page"), USER_PERMS)
                        .param("filter", "{\"username\":{\"$like\":\"bo\"}}")
                        .param("roleId", "10")
                        .param("order", "{\"id\":\"ASC\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].username").value("bob"))
                .andExpect(jsonPath("$.data.records[0].password").doesNotExist());

        mockMvc.perform(auth(get("/platform/admin/user/page"), USER_PERMS)
                        .param("filter", "{\"p.realName\":{\"$like\":\"测试\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].profile.realName").value("测试用户"));

        mockMvc.perform(auth(get("/platform/admin/user/page"), USER_PERMS)
                        .param("filter", "%7B%22id%22%3A%22%22%2C%22%24and%22%3A%7B%7D%7D")
                        .param("roleId", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(auth(delete("/platform/admin/user/" + userId), USER_PERMS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(auth(get("/platform/admin/user/" + userId), USER_PERMS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }

    @Test
    void pageRejectsPasswordFilter() throws Exception {
        mockMvc.perform(auth(get("/platform/admin/user/page"), USER_PERMS)
                        .param("filter", "{\"password\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(-1));
    }

    @Test
    void roleCrud() throws Exception {
        MvcResult created = mockMvc.perform(auth(post("/platform/admin/role/save"), ROLE_PERMS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"auditor\",\"name\":\"审计\",\"remark\":\"只读审计\",\"sortNo\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("auditor"))
                .andExpect(jsonPath("$.data.remark").value("只读审计"))
                .andExpect(jsonPath("$.data.sortNo").value(20))
                .andReturn();
        long roleId = objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(auth(post("/platform/admin/role/save"), ROLE_PERMS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + roleId + ",\"name\":\"审计员\"}"))
                .andExpect(jsonPath("$.data.name").value("审计员"));

        mockMvc.perform(auth(delete("/platform/admin/role/" + roleId), ROLE_PERMS))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void oauthClientSecretIsOneTime() throws Exception {
        mockMvc.perform(auth(get("/platform/admin/oauth-client/grant-types"), CLIENT_PERMS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        mockMvc.perform(auth(get("/platform/admin/oauth-client/scopes"), CLIENT_PERMS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        MvcResult created = mockMvc.perform(auth(post("/platform/admin/oauth-client/save"), CLIENT_PERMS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"kiosk","clientName":"自助机","grantTypes":"client_credentials","scopes":"kiosk"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clientId").value("kiosk"))
                .andExpect(jsonPath("$.data.secretConfigured").value(true))
                .andExpect(jsonPath("$.data.plaintextSecret").exists())
                .andExpect(jsonPath("$.data.clientSecret").doesNotExist())
                .andReturn();
        JsonNode data = objectMapper.readTree(created.getResponse().getContentAsString()).path("data");
        long clientPk = data.path("id").asLong();
        String secret = data.path("plaintextSecret").asString();
        assertNotNull(secret);
        assertFalse(secret.isBlank());

        mockMvc.perform(auth(get("/platform/admin/oauth-client/" + clientPk), CLIENT_PERMS))
                .andExpect(jsonPath("$.data.plaintextSecret").doesNotExist())
                .andExpect(jsonPath("$.data.clientSecret").doesNotExist())
                .andExpect(jsonPath("$.data.secretConfigured").value(true));

        mockMvc.perform(auth(post("/platform/admin/oauth-client/" + clientPk + "/reset-secret"), CLIENT_PERMS))
                .andExpect(jsonPath("$.data.plaintextSecret").exists())
                .andExpect(jsonPath("$.data.clientSecret").doesNotExist());
    }

    @Test
    void tenantCrudAndUpdatesIdZero() throws Exception {
        MvcResult created = mockMvc.perform(auth(post("/platform/admin/tenant/save"), TENANT_PERMS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"alpha\",\"name\":\"甲租户\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.code").value("alpha"))
                .andExpect(jsonPath("$.data.name").value("甲租户"))
                .andExpect(jsonPath("$.data.status").value(1))
                .andReturn();
        long tenantId = objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
        assertTrue(tenantId != 0L);

        mockMvc.perform(auth(post("/platform/admin/tenant/save"), TENANT_PERMS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":0,\"name\":\"系统默认\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(0))
                .andExpect(jsonPath("$.data.code").value("default"))
                .andExpect(jsonPath("$.data.name").value("系统默认"));

        mockMvc.perform(auth(get("/platform/admin/tenant/page"), TENANT_PERMS)
                        .param("filter", "{\"code\":{\"$like\":\"alp\"}}")
                        .param("order", "{\"id\":\"ASC\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].code").value("alpha"));

        mockMvc.perform(auth(post("/platform/admin/tenant/save"), TENANT_PERMS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"alpha\",\"name\":\"重复\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value("租户编码已存在"));

        mockMvc.perform(auth(delete("/platform/admin/tenant/" + tenantId), TENANT_PERMS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(auth(get("/platform/admin/tenant/" + tenantId), TENANT_PERMS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value("租户不存在"));
    }

    @Test
    void resourceAndRoleBinding() throws Exception {
        MvcResult created = mockMvc.perform(auth(post("/platform/admin/resource/save"), RESOURCE_PERMS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"platform:demo:page\",\"name\":\"演示\",\"type\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("platform:demo:page"))
                .andReturn();
        long resourceId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(auth(post("/platform/admin/role/save"), ROLE_PERMS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":10,\"resourceIds\":[" + resourceId + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resourceIds[0]").value(resourceId));

        mockMvc.perform(auth(delete("/platform/admin/resource/" + resourceId), RESOURCE_PERMS))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void resourceTreeAndMine() throws Exception {
        MvcResult parent = mockMvc.perform(auth(post("/platform/admin/resource/save"), RESOURCE_PERMS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"platform:system\",\"name\":\"系统\",\"type\":1,\"sortNo\":0}"))
                .andExpect(status().isOk())
                .andReturn();
        long parentId = objectMapper.readTree(parent.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        mockMvc.perform(auth(post("/platform/admin/resource/save"), RESOURCE_PERMS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"platform:user:page\",\"name\":\"用户\",\"type\":2,\"parentId\":"
                                + parentId + ",\"sortNo\":1}"))
                .andExpect(status().isOk());

        mockMvc.perform(auth(get("/platform/admin/resource/tree"), RESOURCE_PERMS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("platform:system"))
                .andExpect(jsonPath("$.data[0].children[0].code").value("platform:user:page"));

        mockMvc.perform(auth(get("/platform/admin/resource/mine"), "platform:user:page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("platform:system"))
                .andExpect(jsonPath("$.data[0].children[0].code").value("platform:user:page"));

        mockMvc.perform(auth(get("/platform/admin/resource/mine"), "other:code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(get("/platform/admin/resource/mine")
                        .header(HeaderCurrentUserFilter.USER, "0:root")
                        .header(HeaderCurrentUserFilter.ADMIN, "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].children[0].code").value("platform:user:page"));
    }

    @Test
    void dictConfigAndOperLog() throws Exception {
        MvcResult dictParent = mockMvc.perform(auth(post("/platform/admin/dict/save"), DICT_PERMS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dictType":"region","dictCode":"cn","dictLabel":"中国",
                                 "remark":"根节点","sortNo":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remark").value("根节点"))
                .andReturn();
        long dictParentId = objectMapper.readTree(dictParent.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        mockMvc.perform(auth(post("/platform/admin/dict/save"), DICT_PERMS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":" + dictParentId
                                + ",\"dictCode\":\"bj\",\"dictLabel\":\"北京\",\"content\":\"打印模板\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dictType").value("region"))
                .andExpect(jsonPath("$.data.content").value("打印模板"));
        mockMvc.perform(auth(post("/platform/admin/dict/check"), DICT_PERMS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":" + dictParentId + ",\"dictCode\":\"sh\",\"dictLabel\":\"上海\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dictType").value("region"));
        mockMvc.perform(auth(post("/platform/admin/dict/check"), DICT_PERMS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":" + dictParentId + ",\"dictCode\":\"bj\",\"dictLabel\":\"重复\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value("编码已存在，请勿重复添加"));
        mockMvc.perform(auth(get("/platform/admin/dict/page"), "platform:dict:page")
                        .param("parentId", String.valueOf(dictParentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].dictCode").value("bj"))
                .andExpect(jsonPath("$.data.records[0].content").value("打印模板"));
        mockMvc.perform(auth(get("/platform/admin/dict/tree"), DICT_PERMS)
                        .param("dictType", "region"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].dictCode").value("cn"))
                .andExpect(jsonPath("$.data[0].children[0].dictCode").value("bj"));
        mockMvc.perform(auth(delete("/platform/admin/dict/" + dictParentId), DICT_PERMS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value("字典分类存在子类，请先删除子类"));

        mockMvc.perform(auth(post("/platform/admin/config/save"), CONFIG_PERMS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"site","configKey":"site.name","title":"站点名称",
                                 "configValue":"演示","sortNo":5}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configKey").value("site.name"))
                .andExpect(jsonPath("$.data.category").value("site"))
                .andExpect(jsonPath("$.data.title").value("站点名称"))
                .andExpect(jsonPath("$.data.sortNo").value(5));

        MvcResult recorded = mockMvc.perform(auth(post("/platform/admin/oper-log/record"), OPER_LOG_PERMS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"手工补记","traceId":"a1b2c3d4e5f60708","groupCode":"batch-1",
                                 "repeatCount":3,"httpMethod":"POST","httpStatus":202,"success":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("手工补记"))
                .andExpect(jsonPath("$.data.traceId").exists())
                .andExpect(jsonPath("$.data.traceId").value(org.hamcrest.Matchers.not("a1b2c3d4e5f60708")))
                .andExpect(jsonPath("$.data.groupCode").value("batch-1"))
                .andExpect(jsonPath("$.data.repeatCount").value(3))
                .andExpect(jsonPath("$.data.httpStatus").value(202))
                .andReturn();
        long logId = objectMapper.readTree(recorded.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(auth(get("/platform/admin/oper-log/page"), OPER_LOG_PERMS)
                        .param("filter", "{\"title\":{\"$like\":\"手工\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value(logId));
        mockMvc.perform(auth(get("/platform/admin/oper-log/page"), "platform:oper-log:page")
                        .param("collapse", "true")
                        .param("filter", "{\"title\":{\"$like\":\"手工\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value(logId))
                .andExpect(jsonPath("$.data.records[0].repeatCount").value(1));
    }

    @Test
    void annotatedSaveWritesOperLog() throws Exception {
        mockMvc.perform(auth(post("/platform/admin/config/save"), CONFIG_PERMS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configKey\":\"site.name\",\"configValue\":\"演示\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(auth(get("/platform/admin/oper-log/page"), OPER_LOG_PERMS)
                        .param("filter", "{\"title\":{\"$eq\":\"保存配置\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].title").value("保存配置"))
                .andExpect(jsonPath("$.data.records[0].eventType").value("config_change"))
                .andExpect(jsonPath("$.data.records[0].traceId").exists())
                .andExpect(jsonPath("$.data.records[0].repeatCount").value(1))
                .andExpect(jsonPath("$.data.records[0].httpMethod").value("POST"))
                .andExpect(jsonPath("$.data.records[0].success").value(1))
                .andExpect(jsonPath("$.data.records[0].operatorId").value(1))
                .andExpect(jsonPath("$.data.records[0].operatorName").value("alice"));
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String perms) {
        return request.header(HeaderCurrentUserFilter.USER, "1:alice")
                .header(HeaderCurrentUserFilter.PERMS, perms);
    }
}
