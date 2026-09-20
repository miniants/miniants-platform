package cn.miniants.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PermissionInterceptorTest {

    private final PlatformSecurityProperties properties = new PlatformSecurityProperties();
    private final PermissionInterceptor interceptor = new PermissionInterceptor(
            () -> EnforcementMode.from(properties.getEnforcement()),
            new RequestSecurityAuditSink(),
            new AnnotationPermissionPolicy(),
            null,
            null,
            properties::isRejectUnclassified);

    @Test
    void badTokenIsUnauthenticatedWithReason() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/users");
        AccessAuth.stampJwtInvalid(request);
        AuthDeniedException ex = assertThrows(AuthDeniedException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), handler("guarded")));
        assertEquals(401, ex.status().value());
        assertEquals("permission-unauthenticated:bad-token", AccessAuth.reasonOf(request));
        assertEquals(401, AccessAuth.httpStatusOf(request));
    }

    @Test
    void anonymousOnGuardedThrowsEvenInShadow() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/users");
        AuthDeniedException ex = assertThrows(AuthDeniedException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), handler("guarded")));
        assertEquals(401, ex.status().value());
        assertEquals("permission-unauthenticated", AccessAuth.reasonOf(request));
        assertEquals("anon", AccessAuth.actorOf(request));
    }

    @Test
    void anonymousOnUnmarkedThrowsEvenInShadow() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/legacy");
        AuthDeniedException ex = assertThrows(AuthDeniedException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), handler("plain")));
        assertEquals(401, ex.status().value());
        assertEquals("permission-unauthenticated", AccessAuth.reasonOf(request));
    }

    @Test
    void anonymousOnGuardedThrowsInEnforce() throws Exception {
        properties.setEnforcement("enforce");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/users");
        AuthDeniedException ex = assertThrows(AuthDeniedException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), handler("guarded")));
        assertEquals(401, ex.status().value());
        assertEquals("permission-unauthenticated", AccessAuth.reasonOf(request));
    }

    @Test
    void publicAccessStampsPublicOk() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/healthz");
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler("open")));
        assertEquals(AccessAuth.PERMIT, AccessAuth.authOf(request));
        assertEquals("public-ok:anon", AccessAuth.reasonOf(request));
    }

    @Test
    void unmarkedIsUnclassifiedAndAllowedEvenInEnforceWhenRejectDisabled() throws Exception {
        properties.setEnforcement("enforce");
        properties.setRejectUnclassified(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/legacy");
        CurrentUser.bind(request, CurrentUser.user(1L, "alice").build());
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler("plain")));
        assertEquals("permission-unclassified", AccessAuth.reasonOf(request));
        assertEquals(AccessAuth.UNCLASSIFIED, AccessAuth.authOf(request));
    }

    @Test
    void unmarkedIsDeniedInEnforceWhenRejectEnabled() throws Exception {
        properties.setEnforcement("enforce");
        properties.setRejectUnclassified(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/legacy");
        CurrentUser.bind(request, CurrentUser.user(1L, "alice").build());
        AuthDeniedException ex = assertThrows(AuthDeniedException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), handler("plain")));
        assertEquals(403, ex.status().value());
        assertEquals("permission-unclassified", AccessAuth.reasonOf(request));
    }

    @Test
    void userWithCodeIsOk() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/users");
        CurrentUser.bind(request, CurrentUser.user(1L, "alice").permissions(Set.of("demo:ping")).build());
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler("guarded")));
        assertEquals("permission-ok:demo:ping", AccessAuth.reasonOf(request));
        assertEquals(AccessAuth.OK, AccessAuth.authOf(request));
    }

    @Test
    void userMismatchIsForbiddenInEnforce() throws Exception {
        properties.setEnforcement("enforce");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/users");
        CurrentUser.bind(request, CurrentUser.user(1L, "alice").permissions(Set.of("other")).build());
        AuthDeniedException ex = assertThrows(AuthDeniedException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), handler("guarded")));
        assertEquals(403, ex.status().value());
        assertEquals("permission-mismatch:demo:ping", AccessAuth.reasonOf(request));
    }

    @Test
    void sysAdminBypassesCode() throws Exception {
        properties.setEnforcement("enforce");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/users");
        CurrentUser.bind(request, CurrentUser.user(0L, "root").sysAdmin(true).build());
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler("guarded")));
        assertEquals("permission-ok:admin", AccessAuth.reasonOf(request));
    }

    @Test
    void clientMatchesScope() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/device/ping");
        CurrentUser.bind(request, CurrentUser.client("DEMO").scopes(Set.of("kiosk-tch", "updater")).build());
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler("device")));
        assertEquals("scope-ok:kiosk-tch", AccessAuth.reasonOf(request));
    }

    @Test
    void authenticatedAllowsUser() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");
        CurrentUser.bind(request, CurrentUser.user(0L, "root").build());
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler("me")));
        assertEquals("authenticated-ok", AccessAuth.reasonOf(request));
        assertEquals(AccessAuth.OK, AccessAuth.authOf(request));
    }

    @Test
    void authenticatedRejectsAnonymousEvenInShadow() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");
        AuthDeniedException ex = assertThrows(AuthDeniedException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), handler("me")));
        assertEquals(401, ex.status().value());
    }

    @Test
    void ownedResolverModeIsSkippedForOwnedInterceptor() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/student/mine");
        CurrentUser.bind(request, CurrentUser.user(1L, "alice").build());
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler("mineOwned")));
        assertNull(AccessAuth.reasonOf(request));
    }

    @Test
    void protocolAnonymousPathIsPublicEvenWithoutAnnotation() throws Exception {
        PublicAccessPaths paths = new PublicAccessPaths();
        paths.replaceProtocol(List.of("/internal/runtime/**"));
        PermissionInterceptor interceptor = new PermissionInterceptor(
                () -> EnforcementMode.SHADOW,
                new RequestSecurityAuditSink(),
                new AnnotationPermissionPolicy(),
                null,
                paths);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/runtime/state");
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler("plain")));
        assertEquals("public-ok:anon", AccessAuth.reasonOf(request));
    }

    @Test
    void errorDispatchIsSkipped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        CurrentUser.bind(request, CurrentUser.user(1L, "alice").build());
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler("plain")));
        assertEquals(null, AccessAuth.reasonOf(request));
    }

    private static HandlerMethod handler(String method) throws Exception {
        return new HandlerMethod(new Fixture(), Fixture.class.getMethod(method));
    }

    static class Fixture {
        @Permission("demo:ping")
        public void guarded() {
        }

        @PublicAccess
        public void open() {
        }

        public void plain() {
        }

        @Permission("kiosk-stu|kiosk-tch")
        public void device() {
        }

        @Authenticated
        public void me() {
        }

        @Authenticated(resolver = DemoResolver.class)
        public void mineOwned() {
        }
    }

    static class DemoResolver implements OwnedResolver {
        @Override
        public Resolution resolve(OwnedRequest request) {
            return new Resolution.Ok(request.user());
        }
    }
}
