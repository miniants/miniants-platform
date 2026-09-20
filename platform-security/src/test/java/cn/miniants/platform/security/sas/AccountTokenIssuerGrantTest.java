package cn.miniants.platform.security.sas;

import cn.miniants.platform.security.account.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountTokenIssuerGrantTest {

    @Test
    void rejectsWhenClientMissingGrant() {
        OAuth2AuthorizationService authorizationService = mock(OAuth2AuthorizationService.class);
        OAuth2TokenGenerator<?> tokenGenerator = mock(OAuth2TokenGenerator.class);
        AccountTokenIssuer issuer = new AccountTokenIssuer(
                authorizationService, tokenGenerator, PlatformGrantTypes.EXTERNAL, true);

        RegisteredClient client = RegisteredClient.withId("1")
                .clientId("demo")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientSecret("{noop}secret")
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .build();
        OAuth2ClientAuthenticationToken principal = new OAuth2ClientAuthenticationToken(
                client, ClientAuthenticationMethod.CLIENT_SECRET_BASIC, null);
        UserAccount account = new UserAccount(1L, "alice", "hash", "Alice", true, false, List.of(), 1L);

        assertThrows(OAuth2AuthenticationException.class, () -> issuer.issue(principal, account));
    }
}
