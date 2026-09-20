package cn.miniants.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtCurrentUserFilterTest {

    @Test
    void bindsUserFromBearerClaims() throws Exception {
        JwtDecoder decoder = token -> jwt(builder -> builder
                .claim("userId", 0)
                .claim("username", "root")
                .claim("name", "系统")
                .claim("sysAdmin", 1)
                .claim("roleIds", List.of(10, 20))
                .claim("client_id", "platform-demo"));
        CurrentUser user = bind("Bearer tok", decoder);
        assertEquals(0L, user.userId());
        assertEquals("root", user.username());
        assertEquals("系统", user.name());
        assertTrue(user.sysAdmin());
        assertEquals(List.of(10L, 20L), user.roleIds());
        assertEquals("platform-demo", user.clientId());
        assertTrue(user.permissions().isEmpty());
        assertEquals(Actor.USER, user.actor());
    }


    @Test
    void fallsBackToRealNameClaim() throws Exception {
        JwtDecoder decoder = token -> jwt(builder -> builder
                .claim("userId", 12)
                .claim("username", "teacher")
                .claim("realName", "教师"));
        CurrentUser user = bind("Bearer tok", decoder);
        assertEquals("教师", user.name());
    }

    @Test
    void bindsClientFromScopeClaim() throws Exception {
        JwtDecoder decoder = token -> jwt(builder -> builder
                .subject("kiosk")
                .claim("scope", "demo:ping updater"));
        CurrentUser user = bind("bearer tok", decoder);
        assertEquals("kiosk", user.clientId());
        assertEquals(Actor.CLIENT, user.actor());
        assertTrue(user.scopes().contains("demo:ping"));
        assertTrue(user.scopes().contains("updater"));
    }

    @Test
    void keepsAlreadyBoundUser() throws Exception {
        JwtDecoder decoder = token -> jwt(builder -> builder
                .claim("userId", 2)
                .claim("username", "bob"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer tok");
        CurrentUser.bind(request, CurrentUser.user(9L, "header").build());
        new JwtCurrentUserFilter(decoder).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertEquals(9L, CurrentUser.from(request).userId());
        assertEquals("header", CurrentUser.from(request).username());
    }

    @Test
    void badTokenStaysAnonymous() throws Exception {
        JwtDecoder decoder = token -> {
            throw new JwtException("expired");
        };
        assertNull(bind("Bearer bad", decoder));
    }

    @Test
    void missingBearerDoesNotBind() throws Exception {
        assertNull(bind(null, token -> jwt(builder -> builder.claim("userId", 1))));
    }

    private static CurrentUser bind(String authorization, JwtDecoder decoder) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        new JwtCurrentUserFilter(decoder).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return CurrentUser.from(request);
    }

    private static Jwt jwt(java.util.function.Consumer<Jwt.Builder> customizer) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("tok")
                .header("alg", "none")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600));
        customizer.accept(builder);
        return builder.build();
    }
}
