package cn.miniants.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本人数据归属（{@code @Authenticated(resolver=…)}）黄金测试。
 * 覆盖两种形态（主体即本人 / 资源归属）、off/shadow/enforce 三态、
 * 匿名/客户端/用户/sysAdmin 四类主体、query 与路径变量取参、公开优先、类级注解。
 */
class OwnedInterceptorTest {

    private final PlatformSecurityProperties properties = new PlatformSecurityProperties();

    private OwnedInterceptor interceptor() {
        return interceptor(properties.getOwned().isAdminBypass());
    }

    private OwnedInterceptor interceptor(boolean adminBypass) {
        StaticApplicationContext context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("demoOwnedResolver", new DemoOwnedResolver());
        context.getBeanFactory().registerSingleton("unresolvedResolver", new UnresolvedResolver());
        context.getBeanFactory().registerSingleton("notOwnerResolver", new NotOwnerResolver());
        context.refresh();
        return new OwnedInterceptor(
                () -> EnforcementMode.from(properties.getEnforcement()),
                new RequestSecurityAuditSink(),
                null,
                context,
                adminBypass);
    }

    @Test
    void plainAuthenticatedIsNotHandledByOwned() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");
        assertTrue(interceptor().preHandle(request, new MockHttpServletResponse(), handler("me")));
        assertNull(AccessAuth.reasonOf(request));
        assertNull(OwnedContext.from(request));
    }

    @Test
    void userOwnedOkBindsContextAndStamps() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/student/mine");
        CurrentUser.bind(request, CurrentUser.user(1L, "alice").build());
        assertTrue(interceptor().preHandle(request, new MockHttpServletResponse(), handler("mine")));
        assertEquals("owned-ok:demo", AccessAuth.reasonOf(request));
        assertEquals(AccessAuth.OK, AccessAuth.authOf(request));
        OwnedContext ctx = OwnedContext.from(request);
        assertEquals("subject-of-alice", ctx.subject());
        assertEquals("alice", ctx.user().username());
    }

    @Test
    void ownedParamFromQueryOk() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/student/mine-by-id");
        request.addParameter("id", "alice");
        CurrentUser.bind(request, CurrentUser.user(1L, "alice").build());
        assertTrue(interceptor().preHandle(request, new MockHttpServletResponse(), handler("mineById")));
        assertEquals("owned-ok:demo", AccessAuth.reasonOf(request));
    }

    @Test
    void ownedParamFromPathVariableOk() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/student/mine-by-id");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("id", "alice"));
        CurrentUser.bind(request, CurrentUser.user(1L, "alice").build());
        assertTrue(interceptor().preHandle(request, new MockHttpServletResponse(), handler("mineById")));
        assertEquals("owned-ok:demo", AccessAuth.reasonOf(request));
    }

    @Test
    void ownedParamMismatchShadowAllowedWithReason() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/student/mine-by-id");
        request.addParameter("id", "bob");
        CurrentUser.bind(request, CurrentUser.user(1L, "alice").build());
        assertTrue(interceptor().preHandle(request, new MockHttpServletResponse(), handler("mineById")));
        assertEquals("owned-not-owner", AccessAuth.reasonOf(request));
        assertEquals(AccessAuth.DENY, AccessAuth.authOf(request));
    }

    @Test
    void ownedParamMismatchForbiddenInEnforce() throws Exception {
        properties.setEnforcement("enforce");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/student/mine-by-id");
        request.addParameter("id", "bob");
        CurrentUser.bind(request, CurrentUser.user(1L, "alice").build());
        AuthDeniedException ex = assertThrows(AuthDeniedException.class,
                () -> interceptor().preHandle(request, new MockHttpServletResponse(), handler("mineById")));
        assertEquals(403, ex.status().value());
        assertEquals("owned-not-owner", AccessAuth.reasonOf(request));
    }

    @Test
    void ownedMissingParamIsUnresolved() throws Exception {
        properties.setEnforcement("enforce");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/student/mine-by-id");
        CurrentUser.bind(request, CurrentUser.user(1L, "alice").build());
        AuthDeniedException ex = assertThrows(AuthDeniedException.class,
                () -> interceptor().preHandle(request, new MockHttpServletResponse(), handler("mineById")));
        assertEquals(401, ex.status().value());
        assertEquals("无法确认本人身份", ex.getMessage());
        assertEquals("owned-unresolved", AccessAuth.reasonOf(request));
    }

    @Test
    void unresolvedShadowAllowedWithReason() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/student/cannot");
        CurrentUser.bind(request, CurrentUser.user(1L, "alice").build());
        assertTrue(interceptor().preHandle(request, new MockHttpServletResponse(), handler("cannotResolve")));
        assertEquals("owned-unresolved", AccessAuth.reasonOf(request));
        assertEquals(AccessAuth.DENY, AccessAuth.authOf(request));
    }

    @Test
    void unresolvedForbiddenInEnforce() throws Exception {
        properties.setEnforcement("enforce");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/student/cannot");
        CurrentUser.bind(request, CurrentUser.user(1L, "alice").build());
        AuthDeniedException ex = assertThrows(AuthDeniedException.class,
                () -> interceptor().preHandle(request, new MockHttpServletResponse(), handler("cannotResolve")));
        assertEquals(401, ex.status().value());
        assertEquals("无法确认本人身份", ex.getMessage());
    }

    @Test
    void notOwnerForbiddenInEnforce() throws Exception {
        properties.setEnforcement("enforce");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/student/not-mine");
        CurrentUser.bind(request, CurrentUser.user(1L, "alice").build());
        AuthDeniedException ex = assertThrows(AuthDeniedException.class,
                () -> interceptor().preHandle(request, new MockHttpServletResponse(), handler("notMine")));
        assertEquals(403, ex.status().value());
        assertEquals("owned-not-owner", AccessAuth.reasonOf(request));
    }

    @Test
    void anonymousForbiddenEvenInShadow() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/student/mine");
        AuthDeniedException ex = assertThrows(AuthDeniedException.class,
                () -> interceptor().preHandle(request, new MockHttpServletResponse(), handler("mine")));
        assertEquals(401, ex.status().value());
        assertEquals("未登录", ex.getMessage());
        assertEquals("owned-unauthenticated", AccessAuth.reasonOf(request));
        assertEquals(AccessAuth.DENY, AccessAuth.authOf(request));
    }

    @Test
    void anonymousForbiddenInEnforce() throws Exception {
        properties.setEnforcement("enforce");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/student/mine");
        AuthDeniedException ex = assertThrows(AuthDeniedException.class,
                () -> interceptor().preHandle(request, new MockHttpServletResponse(), handler("mine")));
        assertEquals(401, ex.status().value());
        assertEquals("未登录", ex.getMessage());
        assertEquals("owned-unauthenticated", AccessAuth.reasonOf(request));
    }

    @Test
    void clientShadowAllowedWithReason() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/student/mine");
        CurrentUser.bind(request, CurrentUser.client("DEMO").build());
        assertTrue(interceptor().preHandle(request, new MockHttpServletResponse(), handler("mine")));
        assertEquals("owned-not-user", AccessAuth.reasonOf(request));
        assertEquals(AccessAuth.DENY, AccessAuth.authOf(request));
    }

    @Test
    void clientForbiddenInEnforce() throws Exception {
        properties.setEnforcement("enforce");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/student/mine");
        CurrentUser.bind(request, CurrentUser.client("DEMO").build());
        AuthDeniedException ex = assertThrows(AuthDeniedException.class,
                () -> interceptor().preHandle(request, new MockHttpServletResponse(), handler("mine")));
        assertEquals(403, ex.status().value());
        assertEquals("owned-not-user", AccessAuth.reasonOf(request));
    }

    @Test
    void sysAdminBypassesResolverByDefault() throws Exception {
        properties.setEnforcement("enforce");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/student/mine-by-id");
        request.addParameter("id", "bob");
        CurrentUser.bind(request, CurrentUser.user(0L, "root").sysAdmin(true).build());
        assertTrue(interceptor().preHandle(request, new MockHttpServletResponse(), handler("mineById")));
        assertEquals("owned-ok:admin", AccessAuth.reasonOf(request));
        assertNull(OwnedContext.from(request).subject());
    }

    @Test
    void adminBypassDisabledGoesThroughResolver() throws Exception {
        properties.setEnforcement("enforce");
        properties.getOwned().setAdminBypass(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/student/mine");
        CurrentUser.bind(request, CurrentUser.user(0L, "root").sysAdmin(true).build());
        assertTrue(interceptor(false).preHandle(request, new MockHttpServletResponse(), handler("mine")));
        assertEquals("owned-ok:demo", AccessAuth.reasonOf(request));
        assertEquals("subject-of-root", OwnedContext.from(request).subject());
    }

    @Test
    void publicAccessWinsOverOwned() throws Exception {
        properties.setEnforcement("enforce");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/public/owned");
        assertTrue(interceptor().preHandle(request, new MockHttpServletResponse(), handler("openOwned")));
        // OwnedInterceptor 对公开档只跳过不盖章；public-ok 由 PermissionInterceptor 盖
        assertNull(AccessAuth.reasonOf(request));
        assertNull(AccessAuth.authOf(request));
        assertNull(OwnedContext.from(request));
    }

    @Test
    void offModePassesWithoutStamp() throws Exception {
        properties.setEnforcement("off");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/student/mine");
        assertTrue(interceptor().preHandle(request, new MockHttpServletResponse(), handler("mine")));
        assertNull(AccessAuth.reasonOf(request));
    }

    @Test
    void classLevelOwnedAppliesToAllMethods() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/class-owned/ping");
        CurrentUser.bind(request, CurrentUser.user(1L, "alice").build());
        HandlerMethod handler = new HandlerMethod(new ClassOwnedFixture(), ClassOwnedFixture.class.getMethod("ping"));
        assertTrue(interceptor().preHandle(request, new MockHttpServletResponse(), handler));
        assertEquals("owned-ok:demo", AccessAuth.reasonOf(request));
        assertEquals("subject-of-alice", OwnedContext.from(request).subject());
    }

    @Test
    void accessAuthClassifiesOwnedReasons() {
        assertEquals(AccessAuth.DENY, AccessAuth.authFromReason("owned-not-owner"));
        assertEquals(AccessAuth.DENY, AccessAuth.authFromReason("owned-not-user"));
        assertEquals(AccessAuth.DENY, AccessAuth.authFromReason("owned-unresolved"));
        assertEquals(AccessAuth.DENY, AccessAuth.authFromReason("owned-unauthenticated"));
        assertEquals(AccessAuth.OK, AccessAuth.authFromReason("owned-ok"));
        assertEquals(AccessAuth.OK, AccessAuth.authFromReason("owned-ok:student"));
        assertEquals(AccessAuth.OK, AccessAuth.authFromReason("owned-ok:admin"));
    }

    private static HandlerMethod handler(String method) throws Exception {
        return new HandlerMethod(new Fixture(), Fixture.class.getMethod(method));
    }

    static class Fixture {

        @Authenticated(resolver = DemoOwnedResolver.class)
        public void mine() {
        }

        @Authenticated(resolver = DemoOwnedResolver.class, param = "id")
        public void mineById() {
        }

        @Authenticated(resolver = UnresolvedResolver.class)
        public void cannotResolve() {
        }

        @Authenticated(resolver = NotOwnerResolver.class)
        public void notMine() {
        }

        @Authenticated(resolver = DemoOwnedResolver.class, param = "id")
        @PublicAccess
        public void openOwned() {
        }

        @Authenticated
        public void me() {
        }
    }

    @Authenticated(resolver = DemoOwnedResolver.class)
    static class ClassOwnedFixture {
        public void ping() {
        }
    }

    static class DemoOwnedResolver implements OwnedResolver {
        @Override
        public Resolution resolve(OwnedRequest request) {
            if (request.resourceKey() != null && !request.resourceKey().equals(request.user().username())) {
                return new Resolution.NotOwner("belongs to someone else");
            }
            return new Resolution.Ok("subject-of-" + request.user().username());
        }

        @Override
        public String okSuffix() {
            return "demo";
        }
    }

    static class UnresolvedResolver implements OwnedResolver {
        @Override
        public Resolution resolve(OwnedRequest request) {
            return new Resolution.Unresolved("no subject");
        }
    }

    static class NotOwnerResolver implements OwnedResolver {
        @Override
        public Resolution resolve(OwnedRequest request) {
            return new Resolution.NotOwner("not yours");
        }
    }
}
