package cn.miniants.platform.security;

import cn.miniants.platform.core.api.ApiResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlatformSecurityAdviceTest {

    private final PlatformSecurityAdvice advice = new PlatformSecurityAdvice();

    @Test
    void overriddenMessageGoesToEnvelope() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthDeniedException ex = new AuthDeniedException(
                SecurityCodes.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "无法确认本人身份");
        ApiResult<Object> body = advice.handleDenied(ex, response);
        assertEquals(401, response.getStatus());
        assertEquals(-1, body.getCode());
        assertEquals("无法确认本人身份", body.getMessage());
    }

    @Test
    void unboundStaysHttp200() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        UnboundAccountException ex = UnboundAccount.exception("abcdefghijklmnopqrstuvwxyz12", null);
        ApiResult<Object> body = advice.handleUnbound(ex, response);
        assertEquals(200, response.getStatus());
        assertEquals(-1, body.getCode());
        assertEquals("未绑定账号（openid后8位=uvwxyz12）", body.getMessage());
    }

    @Test
    void defaultCodeStillSaysNotLoggedIn() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthDeniedException ex = new AuthDeniedException(
                SecurityCodes.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED);
        ApiResult<Object> body = advice.handleDenied(ex, response);
        assertEquals(401, response.getStatus());
        assertEquals("未登录", body.getMessage());
    }
}
