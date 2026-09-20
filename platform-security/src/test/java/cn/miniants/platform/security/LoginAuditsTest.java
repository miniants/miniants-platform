package cn.miniants.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LoginAuditsTest {

    @Test
    void titlesFollowGrant() {
        assertEquals("登录成功", LoginAudits.title(LoginAudits.PASSWORD, true));
        assertEquals("外部身份登录失败", LoginAudits.title(LoginAudits.EXTERNAL, false));
        assertEquals("扫码登录成功", LoginAudits.title(LoginAudits.QR, true));
        assertEquals("刷新令牌失败", LoginAudits.title(LoginAudits.REFRESH, false));
        assertEquals("客户端鉴权失败", LoginAudits.title(LoginAudits.CLIENT_SECRET, false));
        assertEquals("身份证实名登录成功", LoginAudits.title(LoginAudits.ID_NO, true));
        assertEquals("登录成功", LoginAudits.title("custom_grant", true));
    }

    @Test
    void clientIdFromBasicAuthDoesNotIncludeSecret() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String token = Base64.getEncoder().encodeToString("APP_KIOSK:s3cret".getBytes(StandardCharsets.UTF_8));
        request.addHeader("Authorization", "Basic " + token);
        assertEquals("APP_KIOSK", LoginAudits.clientIdOf(request));
    }

    @Test
    void clientIdPrefersFormParam() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("client_id", "APP_WEB");
        assertEquals("APP_WEB", LoginAudits.clientIdOf(request));
    }

    @Test
    void userIdParsesNumberOrString() {
        assertEquals(9L, LoginAudits.userIdOf(9));
        assertEquals(9L, LoginAudits.userIdOf("9"));
        assertNull(LoginAudits.userIdOf("x"));
    }
}
