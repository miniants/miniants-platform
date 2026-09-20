package cn.miniants.platform.admin.support;

import cn.miniants.platform.security.AccessAuth;
import cn.miniants.platform.security.Actor;
import cn.miniants.platform.security.CurrentUser;
import cn.miniants.platform.security.EnforcementMode;
import cn.miniants.platform.security.LoginAttempt;
import cn.miniants.platform.security.OperLogEntry;
import cn.miniants.platform.security.OperLogEventTypes;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminSecurityAuditSinkTest {

    @Test
    void persistEventTypeKeepsOnlyHighValue() {
        assertEquals(OperLogEventTypes.AUTH_UNCLASSIFIED,
                AdminSecurityAuditSink.persistEventType("permission-unclassified"));
        assertEquals(OperLogEventTypes.AUTH_DENY,
                AdminSecurityAuditSink.persistEventType("permission-mismatch:x"));
        assertEquals(OperLogEventTypes.AUTH_DENY,
                AdminSecurityAuditSink.persistEventType("owned-not-owner"));
        assertNull(AdminSecurityAuditSink.persistEventType("permission-ok:x"));
        assertNull(AdminSecurityAuditSink.persistEventType("public-ok:anon"));
    }

    @Test
    void unclassifiedIsPersistedAndStamped() {
        List<OperLogEntry> recorded = new ArrayList<>();
        AdminSecurityAuditSink sink = new AdminSecurityAuditSink(recorded::add);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/ping");
        CurrentUser.bind(request, CurrentUser.user(12L, "alice").clientId("web").build());

        sink.record(request, "permission-unclassified", Actor.USER);

        assertEquals(AccessAuth.UNCLASSIFIED, AccessAuth.authOf(request));
        assertEquals(1, recorded.size());
        OperLogEntry entry = recorded.get(0);
        assertEquals("未分类", entry.title());
        assertEquals(OperLogEventTypes.AUTH_UNCLASSIFIED, entry.eventType());
        assertEquals("alice/web", entry.operatorName());
        assertEquals(12L, entry.operatorId());
        assertTrue(entry.success());
        assertEquals(200, entry.httpStatus());
        assertEquals("GET", entry.httpMethod());
        assertEquals("/admin/ping", entry.requestUri());
        assertTrue(entry.requestParam() == null || entry.requestParam().contains("enforcement="));
    }

    @Test
    void permitIsStampedButNotPersisted() {
        List<OperLogEntry> recorded = new ArrayList<>();
        AdminSecurityAuditSink sink = new AdminSecurityAuditSink(recorded::add);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/open");

        sink.record(request, "public-ok:anon", Actor.ANON);

        assertEquals(AccessAuth.PERMIT, AccessAuth.authOf(request));
        assertTrue(recorded.isEmpty());
    }

    @Test
    void loginPersistsGenericFields() {
        List<OperLogEntry> recorded = new ArrayList<>();
        AdminSecurityAuditSink sink = new AdminSecurityAuditSink(recorded::add);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/open/web/password");
        request.addHeader("X-Forwarded-For", "10.0.0.8");

        sink.login(request, false, "alice/web", "用户名或密码错误", "登录失败");

        assertEquals("fail", AccessAuth.loginOf(request));
        assertEquals(1, recorded.size());
        OperLogEntry entry = recorded.get(0);
        assertEquals("登录失败", entry.title());
        assertEquals(OperLogEventTypes.LOGIN, entry.eventType());
        assertEquals("alice/web", entry.operatorName());
        assertEquals("10.0.0.8", entry.requestIp());
        assertEquals("用户名或密码错误", entry.errorMessage());
        assertEquals(401, entry.httpStatus());
        assertEquals("POST", entry.httpMethod());
        assertEquals("/auth/open/web/password", entry.requestUri());
    }

    @Test
    void loginAttemptFillsGrantAndFormattedUser() {
        List<OperLogEntry> recorded = new ArrayList<>();
        AdminSecurityAuditSink sink = new AdminSecurityAuditSink(recorded::add);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/open/miniapp/external");

        sink.login(request, new LoginAttempt(
                true, "alice", "JWY_MINIAPP", 9L, "external", null, "外部身份登录成功", 200));

        OperLogEntry entry = recorded.get(0);
        assertEquals("alice/JWY_MINIAPP", entry.operatorName());
        assertEquals(9L, entry.operatorId());
        assertEquals("grant=external", entry.requestParam());
        assertEquals(200, entry.httpStatus());
        assertTrue(entry.success());
        assertEquals("ok", AccessAuth.loginOf(request));
    }

    @Test
    void unboundLoginStampsUnboundNotFail() {
        List<OperLogEntry> recorded = new ArrayList<>();
        AdminSecurityAuditSink sink = new AdminSecurityAuditSink(recorded::add);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/open/miniapp/external");

        sink.login(request, LoginAttempt.of(
                false, null, "JWY_MINIAPP", "external", "未绑定账号（openid后8位=uvwxyz12）"));

        assertEquals("unbound", AccessAuth.loginOf(request));
        assertEquals(1, recorded.size());
        assertFalse(recorded.get(0).success());
    }

    @Test
    void shadowDenyRecordsHttp200() {
        List<OperLogEntry> recorded = new ArrayList<>();
        AdminSecurityAuditSink sink = new AdminSecurityAuditSink(recorded::add);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/users");
        CurrentUser.bind(request, CurrentUser.user(1L, "alice").clientId("JWY_WEB").build());
        AccessAuth.stampEnforcement(request, EnforcementMode.SHADOW);
        AccessAuth.stampHttpStatus(request, 200);

        sink.record(request, "permission-mismatch:demo:ping", Actor.USER);

        OperLogEntry entry = recorded.get(0);
        assertEquals(OperLogEventTypes.AUTH_DENY, entry.eventType());
        assertEquals(200, entry.httpStatus());
        assertFalse(entry.success());
        assertEquals("enforcement=shadow", entry.requestParam());
    }
}
