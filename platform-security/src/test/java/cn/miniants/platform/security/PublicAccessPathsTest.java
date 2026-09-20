package cn.miniants.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicAccessPathsTest {

    @Test
    void matchesMappedPathOnly() {
        PublicAccessPaths paths = PublicAccessPaths.of(List.of(
                "/integration/open/pay/callback",
                "/sys/client/updater/{appName}/release/{filename}"));
        assertTrue(paths.matches("/integration/open/pay/callback"));
        assertTrue(paths.matches("/sys/client/updater/JWY_STU_KIOSK/release/latest.yml"));
        assertFalse(paths.matches("/sys/client/updater/JWY_STU_KIOSK/upload"));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/other");
        assertFalse(paths.matches(request, "/other"));
        assertTrue(paths.matches(request, "/integration/open/pay/callback"));
    }

    @Test
    void protocolPatternsSurviveMappingReplace() {
        PublicAccessPaths paths = new PublicAccessPaths();
        paths.replaceProtocol(List.of("/oauth/**", "/login/**"));
        paths.replace(List.of("/integration/open/pay/callback"));
        assertTrue(paths.matches("/oauth/token"));
        assertTrue(paths.matches("/login/oauth2"));
        assertTrue(paths.matches("/integration/open/pay/callback"));
        assertFalse(paths.matches("/system/admin/user/page"));
    }
}
