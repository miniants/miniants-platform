package cn.miniants.platform.security.bff;

import cn.miniants.platform.core.error.PlatformException;
import cn.miniants.platform.security.CurrentUser;
import cn.miniants.platform.security.LoginAttempt;
import cn.miniants.platform.security.LoginAudits;
import cn.miniants.platform.security.PlatformSecurityProperties;
import cn.miniants.platform.security.SecurityAuditSink;
import cn.miniants.platform.security.account.UserAccount;
import cn.miniants.platform.security.account.UserAccountService;
import cn.miniants.platform.security.person.PrincipalDirectory;
import cn.miniants.platform.security.person.PrincipalSummary;
import cn.miniants.platform.security.sas.AccountTokenIssuer;
import cn.miniants.platform.security.sas.PasswordGrantAuthenticationProvider;
import cn.miniants.platform.security.sas.PlatformGrantTypes;
import cn.miniants.platform.security.sas.PasswordGrantAuthenticationToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultInternalTokenIssuer implements UserTokenIssuer {

    private static final Logger log = LoggerFactory.getLogger(DefaultInternalTokenIssuer.class);

    private final RegisteredClientRepository registeredClientRepository;
    private final PasswordGrantAuthenticationProvider passwordGrantAuthenticationProvider;
    private final OAuth2RefreshTokenAuthenticationProvider refreshTokenAuthenticationProvider;
    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;
    private final AuthorizationServerSettings authorizationServerSettings;
    private final UserAccountService userAccountService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<PrincipalDirectory> principalDirectory;
    private final PlatformSecurityProperties securityProperties;
    private final ObjectProvider<SecurityAuditSink> auditSink;

    public DefaultInternalTokenIssuer(
            RegisteredClientRepository registeredClientRepository,
            PasswordGrantAuthenticationProvider passwordGrantAuthenticationProvider,
            OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator,
            AuthorizationServerSettings authorizationServerSettings,
            UserAccountService userAccountService,
            PasswordEncoder passwordEncoder,
            ObjectProvider<PrincipalDirectory> principalDirectory,
            PlatformSecurityProperties securityProperties) {
        this(registeredClientRepository, passwordGrantAuthenticationProvider, authorizationService,
                tokenGenerator, authorizationServerSettings, userAccountService, passwordEncoder,
                principalDirectory, securityProperties, null);
    }

    public DefaultInternalTokenIssuer(
            RegisteredClientRepository registeredClientRepository,
            PasswordGrantAuthenticationProvider passwordGrantAuthenticationProvider,
            OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator,
            AuthorizationServerSettings authorizationServerSettings,
            UserAccountService userAccountService,
            PasswordEncoder passwordEncoder,
            ObjectProvider<PrincipalDirectory> principalDirectory,
            PlatformSecurityProperties securityProperties,
            ObjectProvider<SecurityAuditSink> auditSink) {
        this.registeredClientRepository = registeredClientRepository;
        this.passwordGrantAuthenticationProvider = passwordGrantAuthenticationProvider;
        this.refreshTokenAuthenticationProvider = new OAuth2RefreshTokenAuthenticationProvider(
                authorizationService, tokenGenerator);
        this.authorizationService = authorizationService;
        this.tokenGenerator = tokenGenerator;
        this.authorizationServerSettings = authorizationServerSettings;
        this.userAccountService = userAccountService;
        this.passwordEncoder = passwordEncoder;
        this.principalDirectory = principalDirectory;
        this.securityProperties = securityProperties;
        this.auditSink = auditSink;
    }

    @Override
    public Map<String, Object> issuePassword(String clientId, String username, String password) {
        return issuePassword(clientId, username, password, null);
    }

    @Override
    public Map<String, Object> issuePassword(
            String clientId, String username, String password, String designatedUsername) {
        if (designatedUsername == null || designatedUsername.isBlank()) {
            OAuth2ClientAuthenticationToken client = authenticatedClient(clientId);
            PasswordGrantAuthenticationToken grant = new PasswordGrantAuthenticationToken(
                    client, username, password, Set.of());
            try {
                Map<String, Object> body = issue(
                        () -> passwordGrantAuthenticationProvider.authenticate(grant), "用户名或密码错误");
                UserAccount account = userAccountService.findByUsername(username).orElse(null);
                auditLogin(true, username, clientId, account == null ? null : account.id(),
                        LoginAudits.PASSWORD, null);
                return withPrincipals(body, account);
            } catch (RuntimeException ex) {
                auditLogin(false, username, clientId, null, LoginAudits.PASSWORD, ex.getMessage());
                throw ex;
            }
        }
        UserAccount loginAccount = userAccountService.findByUsername(username).orElse(null);
        if (loginAccount == null || !loginAccount.enabled()
                || !passwordEncoder.matches(password, loginAccount.passwordHash())) {
            auditLogin(false, username, clientId, null, LoginAudits.PASSWORD, "用户名或密码错误");
            throw new PlatformException("用户名或密码错误");
        }
        UserAccount target;
        try {
            target = resolveDesignated(loginAccount, designatedUsername.trim());
        } catch (RuntimeException ex) {
            auditLogin(false, username, clientId, loginAccount.id(), LoginAudits.PASSWORD, ex.getMessage());
            throw ex;
        }
        return issueForAccount(clientId, target, LoginAudits.PASSWORD);
    }

    @Override
    public Map<String, Object> issueRefresh(String clientId, String refreshToken) {
        OAuth2ClientAuthenticationToken client = authenticatedClient(clientId);
        OAuth2RefreshTokenAuthenticationToken grant = new OAuth2RefreshTokenAuthenticationToken(
                refreshToken, client, null, Map.of());
        try {
            Map<String, Object> body = issue(
                    () -> refreshTokenAuthenticationProvider.authenticate(grant), "登录已失效，请重新登录");
            auditLogin(true, LoginAudits.textOf(body.get("username")), clientId,
                    LoginAudits.userIdOf(body.get("userId")), LoginAudits.REFRESH, null);
            return body;
        } catch (RuntimeException ex) {
            auditLogin(false, null, clientId, null, LoginAudits.REFRESH, ex.getMessage());
            throw ex;
        }
    }

    @Override
    public Map<String, Object> issueForAccount(String clientId, UserAccount account) {
        return issueForAccount(clientId, account, null);
    }

    @Override
    public Map<String, Object> issueForAccount(String clientId, UserAccount account, String grant) {
        if (account == null || !account.enabled()) {
            if (grant != null) {
                auditLogin(false, account == null ? null : account.username(), clientId,
                        account == null ? null : account.id(), grant, "账号不可用");
            }
            throw new PlatformException("账号不可用");
        }
        try {
            OAuth2ClientAuthenticationToken client = authenticatedClient(clientId);
            AccountTokenIssuer issuer = accountIssuerForGrant(grant);
            Map<String, Object> body = issue(
                    () -> issuer.issue(client, account), "登录失败");
            if (grant != null) {
                auditLogin(true, account.username(), clientId, account.id(), grant, null);
            }
            return withPrincipals(body, account);
        } catch (PlatformException ex) {
            if (grant != null) {
                auditLogin(false, account.username(), clientId, account.id(), grant, ex.getMessage());
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (grant != null) {
                auditLogin(false, account.username(), clientId, account.id(), grant, ex.getMessage());
            }
            log.error("[pl] issue-token username={} userId={} clientId={} -> {}",
                    account.username(), account.id(), clientId, ex.toString(), ex);
            throw ex;
        }
    }

    private AccountTokenIssuer accountIssuerForGrant(String grant) {
        if (grant == null || grant.isBlank()) {
            return new AccountTokenIssuer(authorizationService, tokenGenerator,
                    PlatformGrantTypes.PASSWORD, false);
        }
        AuthorizationGrantType grantType = PlatformGrantTypes.fromValue(grant);
        return new AccountTokenIssuer(authorizationService, tokenGenerator, grantType, true);
    }

    private void auditLogin(
            boolean success, String username, String clientId, Long userId, String grant, String message) {
        SecurityAuditSink sink = auditSink == null ? null : auditSink.getIfAvailable();
        if (sink == null) {
            return;
        }
        LoginAudits.record(sink, CurrentUser.currentRequest(),
                LoginAttempt.of(success, username, clientId, userId, grant, message));
    }

    private UserAccount resolveDesignated(UserAccount loginAccount, String designatedUsername) {
        if (loginAccount.personId() == null) {
            throw new PlatformException("无法选择身份");
        }
        PrincipalDirectory directory = principalDirectory.getIfAvailable();
        if (directory == null) {
            throw new PlatformException("未启用多账号");
        }
        return directory.resolveDesignated(loginAccount.personId(), designatedUsername)
                .orElseThrow(() -> new PlatformException("指定账号无效"));
    }

    private Map<String, Object> withPrincipals(Map<String, Object> body, UserAccount account) {
        if (account == null || account.personId() == null
                || securityProperties == null
                || !securityProperties.getAccount().isMultiPrincipal()) {
            return body;
        }
        PrincipalDirectory directory = principalDirectory.getIfAvailable();
        if (directory == null) {
            return body;
        }
        return attachPrincipals(body, directory.listByPersonId(account.personId()));
    }

    /** 有启用账号就写 principals（含长度 1）；空或 null 不写。不进 JWT。 */
    static Map<String, Object> attachPrincipals(Map<String, Object> body, List<PrincipalSummary> list) {
        if (body == null || list == null || list.isEmpty()) {
            return body;
        }
        List<Map<String, Object>> principals = new ArrayList<>(list.size());
        for (PrincipalSummary summary : list) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("accountId", summary.accountId());
            row.put("username", summary.username());
            row.put("displayName", summary.displayName());
            principals.add(row);
        }
        Map<String, Object> enriched = new LinkedHashMap<>(body);
        enriched.put("principals", principals);
        return enriched;
    }

    private Map<String, Object> issue(TokenCall call, String failureMessage) {
        AuthorizationServerContextHolder.setContext(context());
        try {
            if (!(call.authenticate() instanceof OAuth2AccessTokenAuthenticationToken token)) {
                throw new PlatformException(failureMessage);
            }
            return TokenResponseMaps.from(token);
        } catch (OAuth2AuthenticationException ex) {
            throw new PlatformException(failureMessage);
        } finally {
            AuthorizationServerContextHolder.resetContext();
        }
    }

    private OAuth2ClientAuthenticationToken authenticatedClient(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new PlatformException("登录门面未配置客户端");
        }
        RegisteredClient client = registeredClientRepository.findByClientId(clientId);
        if (client == null) {
            throw new PlatformException("登录门面未配置客户端");
        }
        return new OAuth2ClientAuthenticationToken(
                client, ClientAuthenticationMethod.CLIENT_SECRET_BASIC, client.getClientSecret());
    }

    private AuthorizationServerContext context() {
        return new AuthorizationServerContext() {
            @Override
            public String getIssuer() {
                return authorizationServerSettings.getIssuer();
            }

            @Override
            public AuthorizationServerSettings getAuthorizationServerSettings() {
                return authorizationServerSettings;
            }
        };
    }

    @FunctionalInterface
    private interface TokenCall {
        org.springframework.security.core.Authentication authenticate();
    }
}
