package cn.miniants.platform.security.client;

import java.util.List;

public record OauthClientDescriptor(
        String id,
        String clientId,
        String clientSecret,
        String clientName,
        List<String> grantTypes,
        List<String> scopes,
        int accessTokenTtlSeconds,
        int refreshTokenTtlSeconds,
        boolean enabled
) {
    public OauthClientDescriptor {
        grantTypes = grantTypes == null ? List.of() : List.copyOf(grantTypes);
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }
}
