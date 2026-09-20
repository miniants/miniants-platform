package cn.miniants.platform.demo;

import cn.miniants.platform.security.HeaderCurrentUserFilter;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 三张扩展表（资源路由字段 / 角色数据范围 / 用户资料）的端到端往返。
 *
 * <p>这三块通过可选扩展表承载采用方差异，见 doc/00-architecture/10-kernel.md。
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlatformDemoAdminExtensionTest {

    private static final String USER_PERMS = "platform:user:page,platform:user:save,platform:user:delete";
    private static final String ROLE_PERMS = "platform:role:page,platform:role:save,platform:role:delete";
    private static final String RESOURCE_PERMS = "platform:resource:page,platform:resource:save";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("DELETE FROM sys_user_profile");
        jdbcTemplate.update("DELETE FROM sys_role_data_scope");
        jdbcTemplate.update("DELETE FROM sys_resource_meta");
        jdbcTemplate.update("DELETE FROM sys_user_role");
        jdbcTemplate.update("DELETE FROM sys_role_resource");
        jdbcTemplate.update("DELETE FROM sys_resource");
        jdbcTemplate.update("DELETE FROM sys_role");
        jdbcTemplate.update("DELETE FROM sys_user");
    }

    @Test
    void resourceRouteFieldsSurviveSaveAndShowUpInTheTree() throws Exception {
        long id = save("/platform/admin/resource/save", RESOURCE_PERMS, """
                {"code":"platform:system","name":"系统","type":1,
                 "meta":{"routeName":"system","component":"layout/routerView/parent",
                         "icon":"ele-Setting","keepAlive":true,"affix":true}}
                """);

        mockMvc.perform(auth(get("/platform/admin/resource/" + id), RESOURCE_PERMS))
                .andExpect(jsonPath("$.data.meta.routeName").value("system"))
                .andExpect(jsonPath("$.data.meta.component").value("layout/routerView/parent"))
                .andExpect(jsonPath("$.data.meta.icon").value("ele-Setting"))
                .andExpect(jsonPath("$.data.meta.keepAlive").value(true))
                .andExpect(jsonPath("$.data.meta.affix").value(true))
                .andExpect(jsonPath("$.data.meta.hide").value(false));

        mockMvc.perform(auth(get("/platform/admin/resource/tree"), RESOURCE_PERMS))
                .andExpect(jsonPath("$.data[0].meta.component").value("layout/routerView/parent"));
    }

    /** 覆盖式保存里取消勾选必须真的落库，否则前端上取消的「缓存」永远关不掉。 */
    @Test
    void unsettingABooleanActuallyPersists() throws Exception {
        long id = save("/platform/admin/resource/save", RESOURCE_PERMS, """
                {"code":"platform:a","name":"甲","type":1,"meta":{"keepAlive":true}}
                """);

        mockMvc.perform(auth(post("/platform/admin/resource/save"), RESOURCE_PERMS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + id + ",\"meta\":{\"routeName\":\"a\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meta.keepAlive").value(false));
    }

    /** 不传 meta 是「不动」，不是「清空」——列表页保存基本信息时不该顺手抹掉路由配置。 */
    @Test
    void omittingMetaLeavesTheExistingRouteConfigAlone() throws Exception {
        long id = save("/platform/admin/resource/save", RESOURCE_PERMS, """
                {"code":"platform:b","name":"乙","type":1,"meta":{"icon":"ele-Menu"}}
                """);

        mockMvc.perform(auth(post("/platform/admin/resource/save"), RESOURCE_PERMS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + id + ",\"name\":\"乙改\"}"))
                .andExpect(jsonPath("$.data.name").value("乙改"))
                .andExpect(jsonPath("$.data.meta.icon").value("ele-Menu"));
    }

    @Test
    void roleCarriesItsDataScope() throws Exception {
        long id = save("/platform/admin/role/save", ROLE_PERMS, """
                {"code":"auditor","name":"审计","dataScope":{"scopeType":1,"scopeValue":"11,22"}}
                """);

        mockMvc.perform(auth(get("/platform/admin/role/" + id), ROLE_PERMS))
                .andExpect(jsonPath("$.data.dataScope.scopeType").value(1))
                .andExpect(jsonPath("$.data.dataScope.scopeValue").value("11,22"));
    }

    @Test
    void deletingRoleRemovesItsDataScopeBeforeLogicalDelete() throws Exception {
        long id = save("/platform/admin/role/save", ROLE_PERMS, """
                {"code":"temporary","name":"临时角色","dataScope":{"scopeType":1,"scopeValue":"11"}}
                """);

        mockMvc.perform(auth(delete("/platform/admin/role/" + id), ROLE_PERMS))
                .andExpect(status().isOk());

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_role_data_scope WHERE role_id = ?", Integer.class, id));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_role WHERE id = ? AND deleted IS NOT NULL", Integer.class, id));
    }

    /** 没配数据范围时返回 null 而不是空对象：前端据此区分「不限制」和「限制为空」。 */
    @Test
    void roleWithoutDataScopeReportsNull() throws Exception {
        long id = save("/platform/admin/role/save", ROLE_PERMS, """
                {"code":"plain","name":"普通"}
                """);

        mockMvc.perform(auth(get("/platform/admin/role/" + id), ROLE_PERMS))
                .andExpect(jsonPath("$.data.dataScope").doesNotExist());
    }

    @Test
    void userProfileRoundTrips() throws Exception {
        long id = save("/platform/admin/user/save", USER_PERMS, """
                {"username":"bob","password":"secret",
                 "profile":{"realName":"鲍勃","phone":"13800000000","email":"bob@example.com","sex":"M"}}
                """);

        mockMvc.perform(auth(get("/platform/admin/user/" + id), USER_PERMS))
                .andExpect(jsonPath("$.data.profile.realName").value("鲍勃"))
                .andExpect(jsonPath("$.data.profile.phone").value("13800000000"))
                .andExpect(jsonPath("$.data.profile.sex").value("M"));
    }

    @Test
    void deletingUserRemovesItsProfileBeforeLogicalDelete() throws Exception {
        long id = save("/platform/admin/user/save", USER_PERMS, """
                {"username":"temporary","password":"secret","profile":{"realName":"临时用户"}}
                """);

        mockMvc.perform(auth(delete("/platform/admin/user/" + id), USER_PERMS))
                .andExpect(status().isOk());

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user_profile WHERE user_id = ?", Integer.class, id));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE id = ? AND deleted IS NOT NULL", Integer.class, id));
    }

    /** 改了号码还留着已验证标记，等于把没验证过的号码当成验证过的，找回口令会发到那里。 */
    @Test
    void changingThePhoneClearsItsVerifiedFlag() throws Exception {
        long id = save("/platform/admin/user/save", USER_PERMS, """
                {"username":"carol","password":"secret","profile":{"phone":"13800000000"}}
                """);
        jdbcTemplate.update("UPDATE sys_user_profile SET phone_verified = 1 WHERE user_id = ?", id);

        mockMvc.perform(auth(post("/platform/admin/user/save"), USER_PERMS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + id + ",\"profile\":{\"phone\":\"13900000000\"}}"))
                .andExpect(status().isOk());

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT phone_verified FROM sys_user_profile WHERE user_id = ?", Integer.class, id));
    }

    @Test
    void keepingThePhoneKeepsItsVerifiedFlag() throws Exception {
        long id = save("/platform/admin/user/save", USER_PERMS, """
                {"username":"dave","password":"secret","profile":{"phone":"13800000000"}}
                """);
        jdbcTemplate.update("UPDATE sys_user_profile SET phone_verified = 1 WHERE user_id = ?", id);

        mockMvc.perform(auth(post("/platform/admin/user/save"), USER_PERMS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + id + ",\"profile\":{\"phone\":\"13800000000\",\"realName\":\"戴夫\"}}"))
                .andExpect(status().isOk());

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT phone_verified FROM sys_user_profile WHERE user_id = ?", Integer.class, id));
    }

    private long save(String url, String perms, String body) throws Exception {
        MvcResult result = mockMvc.perform(auth(post(url), perms)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String perms) {
        return request.header(HeaderCurrentUserFilter.USER, "1:alice")
                .header(HeaderCurrentUserFilter.PERMS, perms);
    }
}
