package cn.miniants.platform.security.sas;

import cn.miniants.platform.security.account.UserAccount;
import cn.miniants.platform.security.account.UserAccounts;
import cn.miniants.platform.security.account.UserAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.util.CollectionUtils;

import java.security.Principal;
import java.util.LinkedHashSet;
import java.util.Set;

public class PasswordGrantAuthenticationProvider implements AuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(PasswordGrantAuthenticationProvider.class);

    private final UserAccountService userAccountService;
    private final PasswordEncoder passwordEncoder;
    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;

    public PasswordGrantAuthenticationProvider(UserAccountService userAccountService,
                                               PasswordEncoder passwordEncoder,
                                               OAuth2AuthorizationService authorizationService,
                                               OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator) {
        this.userAccountService = userAccountService;
        this.passwordEncoder = passwordEncoder;
        this.authorizationService = authorizationService;
        this.tokenGenerator = tokenGenerator;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        PasswordGrantAuthenticationToken grant = (PasswordGrantAuthenticationToken) authentication;
        OAuth2ClientAuthenticationToken clientPrincipal = clientPrincipal(grant);
        RegisteredClient registeredClient = clientPrincipal.getRegisteredClient();
        if (registeredClient == null
                || !registeredClient.getAuthorizationGrantTypes().contains(PlatformGrantTypes.PASSWORD)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
        }

        UserAccount user = userAccountService.findByUsername(grant.getUsername()).orElse(null);
        if (user == null || !user.enabled()
                || !passwordEncoder.matches(grant.getPassword(), user.passwordHash())) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }
        rehashIfStale(user, grant.getPassword());
        grant.attach(user);

        Set<String> authorizedScopes = authorizedScopes(registeredClient, grant.getScopes());
        UserAccount stored = UserAccounts.forAuthorizationStore(user);
        UsernamePasswordAuthenticationToken principal = UsernamePasswordAuthenticationToken.authenticated(
                user.username(), null, AuthorityUtils.NO_AUTHORITIES);

        DefaultOAuth2TokenContext.Builder tokenContextBuilder = DefaultOAuth2TokenContext.builder()
                .registeredClient(registeredClient)
                .principal(principal)
                .authorizationServerContext(AuthorizationServerContextHolder.getContext())
                .authorizedScopes(authorizedScopes)
                .authorizationGrantType(PlatformGrantTypes.PASSWORD)
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
        if (registeredClient.getAuthorizationGrantTypes()
                .contains(org.springframework.security.oauth2.core.AuthorizationGrantType.REFRESH_TOKEN)) {
            OAuth2Token generatedRefresh = tokenGenerator.generate(
                    tokenContextBuilder.tokenType(OAuth2TokenType.REFRESH_TOKEN).build());
            if (generatedRefresh instanceof OAuth2RefreshToken token) {
                refreshToken = token;
            }
        }

        OAuth2Authorization.Builder authorizationBuilder = OAuth2Authorization.withRegisteredClient(registeredClient)
                .principalName(user.username())
                .authorizationGrantType(PlatformGrantTypes.PASSWORD)
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

    @Override
    public boolean supports(Class<?> authentication) {
        return PasswordGrantAuthenticationToken.class.isAssignableFrom(authentication);
    }

    /**
     * 把存量弱哈希换成当前编码器的格式。只有验证通过的这一刻手里才有明文，错过就得等用户下次登录，
     * 所以宁可在登录路径上多一次 UPDATE。写失败不能让已经通过的登录失败。
     */
    private void rehashIfStale(UserAccount user, String rawPassword) {
        if (user.id() == null || !passwordEncoder.upgradeEncoding(user.passwordHash())) {
            return;
        }
        try {
            userAccountService.updatePasswordHash(user.id(), passwordEncoder.encode(rawPassword));
        } catch (UnsupportedOperationException ex) {
            // 应用没接改密，留着下次登录再试
        } catch (RuntimeException ex) {
            log.warn("口令重哈希失败，本次登录不受影响: userId={}", user.id(), ex);
        }
    }

    private static Set<String> authorizedScopes(RegisteredClient client, Set<String> requested) {
        if (CollectionUtils.isEmpty(requested)) {
            return client.getScopes();
        }
        for (String scope : requested) {
            if (!client.getScopes().contains(scope)) {
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_SCOPE);
            }
        }
        return new LinkedHashSet<>(requested);
    }

    private static OAuth2ClientAuthenticationToken clientPrincipal(PasswordGrantAuthenticationToken grant) {
        Object principal = grant.getPrincipal();
        if (principal instanceof OAuth2ClientAuthenticationToken client && client.isAuthenticated()) {
            return client;
        }
        throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
    }
}
