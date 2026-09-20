package cn.miniants.platform.security.bff;

import cn.miniants.platform.security.Actor;
import cn.miniants.platform.security.LoginAttempt;
import cn.miniants.platform.security.LoginAudits;
import cn.miniants.platform.security.SecurityAuditSink;
import cn.miniants.platform.security.sas.PlatformSasProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PublicTokenGrantFilterTest {

    @Test
    void deniedBffGrantIsRecorded() throws Exception {
        List<LoginAttempt> recorded = new ArrayList<>();
        PlatformSasProperties properties = new PlatformSasProperties();
        properties.setPublicDeniedGrants(List.of("password", "wechat_miniapp"));
        PublicTokenGrantFilter filter = new PublicTokenGrantFilter(properties, sink(recorded));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth/token");
        request.addParameter("grant_type", "wechat_miniapp");
        request.addParameter("client_id", "JWY_MINIAPP");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertEquals(400, response.getStatus());
        assertEquals(1, recorded.size());
        assertEquals("wechat_miniapp", recorded.get(0).grant());
        assertFalse(recorded.get(0).success());
        assertEquals("JWY_MINIAPP", recorded.get(0).clientId());
    }

    @Test
    void clientCredentialsSuccessIsRecordedAfterChain() throws Exception {
        List<LoginAttempt> recorded = new ArrayList<>();
        PublicTokenGrantFilter filter = new PublicTokenGrantFilter(
                new PlatformSasProperties(), sink(recorded));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth/token");
        request.addParameter("grant_type", "client_credentials");
        request.addParameter("client_id", "JWY_STU_KIOSK");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> ((HttpServletResponse) res).setStatus(200));

        assertEquals(1, recorded.size());
        assertEquals(LoginAudits.CLIENT_CREDENTIALS, recorded.get(0).grant());
        assertEquals(true, recorded.get(0).success());
    }

    @Test
    void clientCredentialsUnauthorizedIsSecretFailure() throws Exception {
        List<LoginAttempt> recorded = new ArrayList<>();
        PublicTokenGrantFilter filter = new PublicTokenGrantFilter(
                new PlatformSasProperties(), sink(recorded));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth/token");
        request.addParameter("grant_type", "client_credentials");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> ((HttpServletResponse) res).setStatus(401));

        assertEquals(LoginAudits.CLIENT_SECRET, recorded.get(0).grant());
        assertFalse(recorded.get(0).success());
    }

    private static SecurityAuditSink sink(List<LoginAttempt> recorded) {
        return new SecurityAuditSink() {
            @Override
            public void record(HttpServletRequest request, String reason, Actor actor) {
            }

            @Override
            public void login(HttpServletRequest request, LoginAttempt attempt) {
                recorded.add(attempt);
            }
        };
    }
}
