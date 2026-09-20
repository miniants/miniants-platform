package cn.miniants.platform.security.sas;

import cn.miniants.platform.security.client.OauthClientDescriptor;
import cn.miniants.platform.security.client.OauthClientLookup;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;

public class PlatformRegisteredClientRepository implements RegisteredClientRepository {

    private final OauthClientLookup lookup;

    public PlatformRegisteredClientRepository(OauthClientLookup lookup) {
        this.lookup = lookup;
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        throw new UnsupportedOperationException("OAuth 客户端只从 sys_oauth_client 读取");
    }

    @Override
    public RegisteredClient findById(String id) {
        return lookup.findById(id).or(() -> lookup.findByClientId(id)).map(this::map).orElse(null);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        return lookup.findByClientId(clientId).map(this::map).orElse(null);
    }

    private RegisteredClient map(OauthClientDescriptor client) {
        if (!client.enabled()) {
            return null;
        }
        RegisteredClient.Builder builder = RegisteredClient.withId(client.id())
                .clientId(client.clientId())
                .clientSecret(prefixSecret(client.clientSecret()))
                .clientName(client.clientName() == null ? client.clientId() : client.clientName())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofSeconds(client.accessTokenTtlSeconds()))
                        .refreshTokenTimeToLive(Duration.ofSeconds(client.refreshTokenTtlSeconds()))
                        .reuseRefreshTokens(true)
                        .build());
        for (String grant : client.grantTypes()) {
            if (!grant.isBlank()) {
                builder.authorizationGrantType(new AuthorizationGrantType(grant.trim()));
            }
        }
        for (String scope : client.scopes()) {
            if (!scope.isBlank()) {
                builder.scope(scope.trim());
            }
        }
        return builder.build();
    }

    private static String prefixSecret(String secret) {
        if (secret == null || secret.startsWith("{")) {
            return secret;
        }
        return "{bcrypt}" + secret;
    }
}
