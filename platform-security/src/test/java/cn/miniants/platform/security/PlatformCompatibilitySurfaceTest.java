package cn.miniants.platform.security;

import cn.miniants.platform.security.sas.PlatformSasProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformCompatibilitySurfaceTest {

    @Test
    void securityDefaultsMatchPublishedSurface() {
        PlatformSecurityProperties properties = new PlatformSecurityProperties();
        assertEquals("shadow", properties.getEnforcement());
        assertTrue(properties.isRejectUnclassified());
        assertTrue(PlatformSecurityProperties.DEFAULT_ANONYMOUS_PATHS.contains("/oauth/**"));
        assertFalse(PlatformSecurityProperties.DEFAULT_ANONYMOUS_PATHS.contains("/rsa/publicKey"));

        PlatformSasProperties sas = new PlatformSasProperties();
        assertFalse(sas.isEnabled());
        assertFalse(sas.isAllowEphemeralKeys());
    }
}
