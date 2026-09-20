package cn.miniants.platform.observability;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpsTest {

    @Test
    void prefersFirstForwardedForHop() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "10.0.0.8, 10.0.0.1");
        request.addHeader("X-Real-IP", "192.168.1.9");
        assertEquals("10.0.0.8", ClientIps.resolve(request));
    }

    @Test
    void fallsBackToRealIpThenRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Real-IP", "192.168.1.9");
        assertEquals("192.168.1.9", ClientIps.resolve(request));

        MockHttpServletRequest remoteOnly = new MockHttpServletRequest();
        remoteOnly.setRemoteAddr("10.0.0.1");
        assertEquals("10.0.0.1", ClientIps.resolve(remoteOnly));
    }

    @Test
    void blanksAndUnsafeCharsBecomeDashOrStripped() {
        assertEquals("-", ClientIps.resolve(null));
        assertEquals("-", ClientIps.sanitize("   "));
        assertEquals("10.0.0.8", ClientIps.sanitize(" 10.0.0.8 "));
        assertEquals("10.0.0.8", ClientIps.sanitize("10.0.0.8|injected"));
        assertEquals("-", ClientIps.sanitize("|||"));
    }
}
