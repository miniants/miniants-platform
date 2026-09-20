package cn.miniants.platform.ratelimit.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientAddressResolverTest {

    @Test
    void ignoresXffWhenPeerNotTrusted() {
        ClientAddressResolver resolver = ClientAddressResolver.of(List.of());
        assertEquals("203.0.113.10", resolver.resolve("203.0.113.10", null, "8.8.8.8, 203.0.113.10"));
    }

    @Test
    void takesRightmostUntrustedWhenPeerTrusted() {
        ClientAddressResolver resolver = ClientAddressResolver.of(List.of("10.0.0.0/8"));
        assertEquals(
                "198.51.100.20",
                resolver.resolve("10.0.0.2", null, "8.8.8.8, 198.51.100.20, 10.0.0.1"));
    }

    @Test
    void prefersForwardedHeader() {
        ClientAddressResolver resolver = ClientAddressResolver.of(List.of("10.0.0.0/8"));
        assertEquals(
                "198.51.100.30",
                resolver.resolve(
                        "10.0.0.5",
                        "for=198.51.100.30;proto=https, for=10.0.0.5",
                        "8.8.8.8"));
    }

    @Test
    void trustsExactIpAndIpv6() {
        ClientAddressResolver resolver = ClientAddressResolver.of(List.of("127.0.0.1", "2001:db8::/32"));
        assertTrue(resolver.isTrusted("127.0.0.1"));
        assertTrue(resolver.isTrusted("2001:db8::1"));
        assertFalse(resolver.isTrusted("192.0.2.1"));
    }
}
