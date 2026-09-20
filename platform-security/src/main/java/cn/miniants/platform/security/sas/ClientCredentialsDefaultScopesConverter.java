package cn.miniants.platform.security.sas;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientCredentialsAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * SAS 0.4+：{@code client_credentials} 未带 {@code scope} 时 authorized scopes 为空。
 * RFC 6749 §3.3 允许授权服务器使用文档化的默认值；未点名时默认定为登记 scope，点了名仍由 Provider 按上限校验。
 */
public class ClientCredentialsDefaultScopesConverter implements AuthenticationConverter {

    @Override
    public Authentication convert(HttpServletRequest request) {
        String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
        if (!AuthorizationGrantType.CLIENT_CREDENTIALS.getValue().equals(grantType)) {
            return null;
        }
        Authentication clientPrincipal = SecurityContextHolder.getContext().getAuthentication();
        if (!(clientPrincipal instanceof OAuth2ClientAuthenticationToken client) || !client.isAuthenticated()) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }
        return applyDefaultScopes(new OAuth2ClientCredentialsAuthenticationToken(
                client, requestedScopes(request), null));
    }

    static Authentication applyDefaultScopes(Authentication converted) {
        if (!(converted instanceof OAuth2ClientCredentialsAuthenticationToken token)) {
            return converted;
        }
        if (!CollectionUtils.isEmpty(token.getScopes())) {
            return token;
        }
        if (!(token.getPrincipal() instanceof OAuth2ClientAuthenticationToken client)
                || !client.isAuthenticated()) {
            return token;
        }
        RegisteredClient registered = client.getRegisteredClient();
        if (registered == null || CollectionUtils.isEmpty(registered.getScopes())) {
            return token;
        }
        return new OAuth2ClientCredentialsAuthenticationToken(
                client, registered.getScopes(), token.getAdditionalParameters());
    }

    private static Set<String> requestedScopes(HttpServletRequest request) {
        String scope = request.getParameter(OAuth2ParameterNames.SCOPE);
        if (!StringUtils.hasText(scope)) {
            return Set.of();
        }
        return new LinkedHashSet<>(Arrays.asList(StringUtils.delimitedListToStringArray(scope, " ")));
    }
}
