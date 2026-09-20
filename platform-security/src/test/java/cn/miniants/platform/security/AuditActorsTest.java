package cn.miniants.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditActorsTest {

    @Test
    void displayNameReadsCurrentUser() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        CurrentUser.bind(request, CurrentUser.user(12L, "alice").name("爱丽丝").clientId("web").build());
        assertEquals("alice", AuditActors.operatorName(CurrentUser.from(request)));
        assertEquals(12L, AuditActors.operatorId(CurrentUser.from(request)));
        assertEquals("alice/web", AuditActors.displayName(request));
    }

    @Test
    void formatUserAndClient() {
        assertEquals("B1204769/web", AuditActors.format("B1204769", "web"));
        assertEquals("-/kiosk", AuditActors.format(null, "kiosk"));
        assertEquals("alice", AuditActors.format("alice", null));
        assertNull(AuditActors.format("  ", ""));
    }

    @Test
    void formatKeepsOversizedHintForAnalysis() {
        String payload = "{${jndi:ldap://x}}" + "a".repeat(80);
        String display = AuditActors.format(payload, "miniapp");
        assertTrue(display.startsWith("{${jndi:ldap://x}}"));
        assertTrue(display.endsWith("/miniapp"));
        assertTrue(display.length() > AuditActors.DISPLAY_MAX);
    }

    @Test
    void clientIpPrefersForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "10.0.0.8, 10.0.0.1");
        request.setRemoteAddr("127.0.0.1");
        assertEquals("10.0.0.8", AuditActors.clientIp(request));
    }
}
