package cn.miniants.platform.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformSecurityPropertiesTest {

    @Test
    void resolvedAnonymousPathsKeepsDefaultsAndAppendsExtras() {
        PlatformSecurityProperties properties = new PlatformSecurityProperties();
        assertTrue(properties.resolvedAnonymousPaths().contains("/oauth/**"));
        assertTrue(properties.resolvedAnonymousPaths().contains("/internal/runtime/**"));

        properties.setAnonymousPaths(List.of("/rsa/publicKey", "  /custom/**  "));
        List<String> resolved = properties.resolvedAnonymousPaths();
        assertTrue(resolved.containsAll(PlatformSecurityProperties.DEFAULT_ANONYMOUS_PATHS));
        assertTrue(resolved.contains("/rsa/publicKey"));
        assertTrue(resolved.contains("/custom/**"));
        assertEquals(PlatformSecurityProperties.DEFAULT_ANONYMOUS_PATHS.size() + 2, resolved.size());
    }
}
