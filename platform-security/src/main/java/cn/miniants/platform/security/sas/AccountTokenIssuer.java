package cn.miniants.platform.security.sas;

import cn.miniants.platform.security.account.UserAccount;
import cn.miniants.platform.security.account.UserAccounts;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.security.Principal;
import java.util.Set;

/**
 * 已解析 {@link UserAccount} 的进程内出票（外部身份 / 扫码 / 学校协议）。
 */
public class AccountTokenIssuer {

    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;
    private final AuthorizationGrantType grantType;
    private final boolean validateGrant;

    public AccountTokenIssuer(
            OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator) {
        this(authorizationService, tokenGenerator, PlatformGrantTypes.PASSWORD, false);
    }

    public AccountTokenIssuer(
            OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator,
            AuthorizationGrantType grantType) {
        this(authorizationService, tokenGenerator, grantType, true);
    }

    public AccountTokenIssuer(
            OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator,
            AuthorizationGrantType grantType,
            boolean validateGrant) {
        this.authorizationService = authorizationService;
        this.tokenGenerator = tokenGenerator;
        this.grantType = grantType == null ? PlatformGrantTypes.PASSWORD : grantType;
        this.validateGrant = validateGrant;
    }

    public OAuth2AccessTokenAuthenticationToken issue(
            OAuth2ClientAuthenticationToken clientPrincipal, UserAccount user) throws AuthenticationException {
        if (user == null || !user.enabled()) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }
        RegisteredClient registeredClient = clientPrincipal.getRegisteredClient();
        if (registeredClient == null) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }
        if (validateGrant && !registeredClient.getAuthorizationGrantTypes().contains(grantType)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
        }
        Set<String> authorizedScopes = registeredClient.getScopes();

        UserAccount stored = UserAccounts.forAuthorizationStore(user);
        ResolvedAccountGrant grant = new ResolvedAccountGrant(clientPrincipal, user, grantType);
        UsernamePasswordAuthenticationToken principal = UsernamePasswordAuthenticationToken.authenticated(
                user.username(), null, AuthorityUtils.NO_AUTHORITIES);

        DefaultOAuth2TokenContext.Builder tokenContextBuilder = DefaultOAuth2TokenContext.builder()
                .registeredClient(registeredClient)
                .principal(principal)
                .authorizationServerContext(AuthorizationServerContextHolder.getContext())
                .authorizedScopes(authorizedScopes)
                .authorizationGrantType(grantType)
                .authorizationGrant(grant);

        OAuth2Token generatedAccess = tokenGenerator.generate(
                tokenContextBuilder.tokenType(OAuth2TokenType.ACCESS_TOKEN).build());
        if (generatedAccess == null) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.SERVER_ERROR);
        }
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                generatedAccess.getTokenValue(),
                generatedAccess.getIssuedAt(),
                generatedAccess.getExpiresAt(),
                authorizedScopes);

        OAuth2RefreshToken refreshToken = null;
        if (registeredClient.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)) {
            OAuth2Token generatedRefresh = tokenGenerator.generate(
                    tokenContextBuilder.tokenType(OAuth2TokenType.REFRESH_TOKEN).build());
            if (generatedRefresh instanceof OAuth2RefreshToken token) {
                refreshToken = token;
            }
        }

        OAuth2Authorization.Builder authorizationBuilder = OAuth2Authorization.withRegisteredClient(registeredClient)
                .principalName(user.username())
                .authorizationGrantType(grantType)
                .authorizedScopes(authorizedScopes)
                .attribute(Principal.class.getName(), principal)
                .attribute(UserAccount.ATTR, stored);
        if (generatedAccess instanceof ClaimAccessor claimAccessor) {
            authorizationBuilder.token(accessToken, metadata ->
                    metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, claimAccessor.getClaims()));
        } else {
            authorizationBuilder.accessToken(accessToken);
        }
        if (refreshToken != null) {
            authorizationBuilder.refreshToken(refreshToken);
        }
        authorizationService.save(authorizationBuilder.build());
        return new OAuth2AccessTokenAuthenticationToken(
                registeredClient, clientPrincipal, accessToken, refreshToken);
    }
}
