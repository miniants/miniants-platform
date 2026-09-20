package cn.miniants.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AccessAuthTest {

    @Test
    void stampsLoginOutcomes() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AccessAuth.stampLogin(request, LoginAttempt.of(true, "alice", "web", "password", null));
        assertEquals("ok", AccessAuth.loginOf(request));

        AccessAuth.stampLogin(request, LoginAttempt.of(
                false, null, "web", "external", "未绑定账号（openid后8位=uvwxyz12）"));
        assertEquals("unbound", AccessAuth.loginOf(request));

        AccessAuth.stampLogin(request, LoginAttempt.of(
                false, "alice", "web", "password", "用户名或密码错误"));
        assertEquals("fail", AccessAuth.loginOf(request));
    }

    @Test
    void ignoresNullAttempt() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AccessAuth.stampLogin(request, (LoginAttempt) null);
        assertNull(AccessAuth.loginOf(request));
    }
}
