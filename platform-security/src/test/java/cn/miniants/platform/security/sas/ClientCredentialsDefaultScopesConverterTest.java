package cn.miniants.platform.security.sas;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientCredentialsAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientCredentialsDefaultScopesConverterTest {

    @Test
    void omittedScopeUsesRegistered() {
        OAuth2ClientAuthenticationToken principal = authenticatedClient();
        OAuth2ClientCredentialsAuthenticationToken request =
                new OAuth2ClientCredentialsAuthenticationToken(principal, Set.of(), Map.of());

        var filled = (OAuth2ClientCredentialsAuthenticationToken)
                ClientCredentialsDefaultScopesConverter.applyDefaultScopes(request);

        assertTrue(filled.getScopes().contains("kiosk-stu"));
        assertTrue(filled.getScopes().contains("updater"));
        assertEquals(Set.of("updater", "kiosk-stu"), filled.getScopes());
    }

    @Test
    void explicitScopeIsUnchanged() {
        OAuth2ClientAuthenticationToken principal = authenticatedClient();
        OAuth2ClientCredentialsAuthenticationToken request =
                new OAuth2ClientCredentialsAuthenticationToken(principal, Set.of("updater"), Map.of());

        assertSame(request, ClientCredentialsDefaultScopesConverter.applyDefaultScopes(request));
        assertEquals(Set.of("updater"), request.getScopes());
    }

    private static OAuth2ClientAuthenticationToken authenticatedClient() {
        RegisteredClient client = RegisteredClient.withId("kiosk")
                .clientId("JWY_STU_KIOSK")
                .clientSecret("{noop}x")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("updater")
                .scope("kiosk-stu")
                .build();
        return new OAuth2ClientAuthenticationToken(
                client, ClientAuthenticationMethod.CLIENT_SECRET_POST, client.getClientSecret());
    }
}
