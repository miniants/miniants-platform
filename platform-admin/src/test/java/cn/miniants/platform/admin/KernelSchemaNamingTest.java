package cn.miniants.platform.admin;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelSchemaNamingTest {

    @Test
    void migrationFollowsKernelNaming() throws Exception {
        String sql = (read("V1__create_kernel_tables.sql")
                + "\n" + read("V2__create_sys_tenant.sql")
                + "\n" + read("V3__create_admin_catalog.sql")
                + "\n" + read("V4__add_oper_log_event_type.sql"))
                .toLowerCase();
        assertFalse(sql.contains("oauth_client\n") && !sql.contains("sys_oauth_client"));
        assertTrue(sql.contains("create table sys_user"));
        assertTrue(sql.contains("create table sys_role"));
        assertTrue(sql.contains("create table sys_user_role"));
        assertTrue(sql.contains("create table sys_oauth_client"));
        assertTrue(sql.contains("create table sys_tenant"));
        assertTrue(sql.contains("create table sys_resource"));
        assertTrue(sql.contains("create table sys_role_resource"));
        assertTrue(sql.contains("create table sys_dict"));
        assertTrue(sql.contains("create table sys_config"));
        assertTrue(sql.contains("create table sys_oper_log"));
        assertTrue(sql.contains("event_type"));
        assertTrue(sql.contains("password     varchar(128)") || sql.contains("password varchar(128)"));
        assertFalse(sql.contains(" salt"));
        assertTrue(sql.contains("deleted"));
        assertTrue(sql.contains("version"));
        assertTrue(sql.contains("create_time"));
        assertTrue(sql.contains("uk_sys_user_username"));
        assertTrue(sql.contains("access_token_ttl"));
        assertFalse(sql.contains("oauth2_registered_client"));

        Matcher tables = Pattern.compile("create table (\\w+)").matcher(sql);
        while (tables.find()) {
            String table = tables.group(1);
            assertTrue(table.startsWith("sys_"), table);
            assertFalse(table.contains("-"), table);
        }
    }

    private static String read(String name) throws Exception {
        return new String(KernelSchemaNamingTest.class.getResourceAsStream("/db/migration/" + name)
                .readAllBytes(), StandardCharsets.UTF_8);
    }
}
