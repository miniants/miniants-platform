package cn.miniants.platform.security.sas;

import cn.miniants.platform.security.account.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformJwtCustomizerTest {

    @Test
    void writesUserClaimsFromPasswordGrant() {
        PasswordGrantAuthenticationToken grant =
                new PasswordGrantAuthenticationToken(null, "alice", "x", java.util.Set.of());
        grant.attach(new UserAccount(1L, "alice", "hash", "Alice", true, false, List.of(10L), null));
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder();
        JwtEncodingContext context = mock(JwtEncodingContext.class);
        when(context.getAuthorizationGrant()).thenReturn(grant);
        when(context.getClaims()).thenReturn(claims);
        when(context.getPrincipal()).thenReturn(null);
        when(context.getAuthorization()).thenReturn(null);

        new PlatformJwtCustomizer().customize(context);
        JwtClaimsSet built = claims.build();
        assertEquals(1L, ((Number) built.getClaim("userId")).longValue());
        assertEquals("alice", built.getClaim("username"));
        assertEquals("Alice", built.getClaim("name"));
        assertEquals(List.of(10L), built.getClaim("roleIds"));
    }

    @Test
    void nameFallsBackToUsernameWhenDisplayNameBlank() {
        PasswordGrantAuthenticationToken grant =
                new PasswordGrantAuthenticationToken(null, "alice", "x", java.util.Set.of());
        grant.attach(new UserAccount(1L, "alice", "hash", null, true, false, List.of(), null));
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder();
        JwtEncodingContext context = mock(JwtEncodingContext.class);
        when(context.getAuthorizationGrant()).thenReturn(grant);
        when(context.getClaims()).thenReturn(claims);
        when(context.getPrincipal()).thenReturn(null);
        when(context.getAuthorization()).thenReturn(null);

        new PlatformJwtCustomizer().customize(context);
        assertEquals("alice", claims.build().getClaim("name"));
    }

    @Test
    void writesUserClaimsFromResolvedAccountGrant() {
        UserAccount user = new UserAccount(2L, "bob", "hash", "Bob", true, false, List.of(20L), 99L);
        ResolvedAccountGrant grant = new ResolvedAccountGrant(null, user, PlatformGrantTypes.PASSWORD);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder();
        JwtEncodingContext context = mock(JwtEncodingContext.class);
        when(context.getAuthorizationGrant()).thenReturn(grant);
        when(context.getClaims()).thenReturn(claims);
        when(context.getAuthorization()).thenReturn(null);

        new PlatformJwtCustomizer().customize(context);
        JwtClaimsSet built = claims.build();
        assertEquals(2L, ((Number) built.getClaim("userId")).longValue());
        assertEquals("bob", built.getClaim("username"));
        assertEquals("Bob", built.getClaim("name"));
    }
}
