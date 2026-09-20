package cn.miniants.platform.security.bff;

import cn.miniants.platform.core.error.PlatformException;
import cn.miniants.platform.security.LoginAttempt;
import cn.miniants.platform.security.LoginAudits;
import cn.miniants.platform.security.SecurityAuditSink;
import cn.miniants.platform.security.UnboundAccount;
import cn.miniants.platform.security.account.UserAccount;
import cn.miniants.platform.security.account.UserAccountService;
import cn.miniants.platform.security.identity.ExternalIdentity;
import cn.miniants.platform.security.identity.ExternalIdentityBinding;
import cn.miniants.platform.security.identity.ExternalIdentityProvider;
import cn.miniants.platform.security.identity.ExternalIdentityProviderRegistry;
import cn.miniants.platform.security.person.PrincipalDirectory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 外部身份换票。采用方可在自己的兼容路径上调用，不必在内核再挂产品专用 URI。
 */
public class BffExternalLoginService {

    private static final String DESIGNATED_USERNAME = "designated_username";

    private final PlatformBffProperties bffProperties;
    private final ExternalIdentityProviderRegistry providerRegistry;
    private final ExternalIdentityBinding binding;
    private final UserAccountService userAccountService;
    private final ObjectProvider<PrincipalDirectory> principalDirectory;
    private final UserTokenIssuer tokenIssuer;
    private final ObjectProvider<SecurityAuditSink> auditSink;

    public BffExternalLoginService(
            PlatformBffProperties bffProperties,
            ExternalIdentityProviderRegistry providerRegistry,
            ExternalIdentityBinding binding,
            UserAccountService userAccountService,
            ObjectProvider<PrincipalDirectory> principalDirectory,
            UserTokenIssuer tokenIssuer,
            ObjectProvider<SecurityAuditSink> auditSink) {
        this.bffProperties = bffProperties;
        this.providerRegistry = providerRegistry;
        this.binding = binding;
        this.userAccountService = userAccountService;
        this.principalDirectory = principalDirectory;
        this.tokenIssuer = tokenIssuer;
        this.auditSink = auditSink;
    }

    public Map<String, Object> login(String audience, Map<String, String> params, HttpServletRequest request) {
        PlatformBffProperties.Audience cfg = bffProperties.getExternal().requireAudience(audience);
        String clientId = cfg.getClientId().trim();
        String designatedUsername = params.get(DESIGNATED_USERNAME);
        Map<String, String> credentials = new HashMap<>(params);
        credentials.remove(DESIGNATED_USERNAME);
        boolean issued = false;
        try {
            ExternalIdentityProvider identityProvider =
                    providerRegistry.resolveConfigured(cfg.getProvider());
            ExternalIdentity resolved = identityProvider.resolve(credentials);
            ExternalIdentity bound = binding.find(resolved.provider(), resolved.subject())
                    .orElseThrow(() -> UnboundAccount.exception(resolved.subject(), designatedUsername));
            UserAccount account = resolveAccount(bound.personId(), designatedUsername, resolved.subject());
            issued = true;
            return tokenIssuer.issueForAccount(clientId, account, LoginAudits.EXTERNAL);
        } catch (RuntimeException ex) {
            if (!issued) {
                LoginAudits.record(auditSink.getIfAvailable(), request,
                        LoginAttempt.of(false, null, clientId, LoginAudits.EXTERNAL, ex.getMessage()));
            }
            throw ex;
        }
    }

    public Map<String, Object> refresh(String audience, String refreshToken, HttpServletRequest request) {
        String clientId = audienceClientId(audience);
        if (refreshToken == null || refreshToken.isBlank()) {
            LoginAudits.record(auditSink.getIfAvailable(), request,
                    LoginAttempt.of(false, null, clientId, LoginAudits.REFRESH, "登录已失效，请重新登录"));
            throw new PlatformException("登录已失效，请重新登录");
        }
        return tokenIssuer.issueRefresh(clientId, refreshToken.trim());
    }

    public String audienceClientId(String audience) {
        if (audience != null && bffProperties.getExternal().getAudiences().containsKey(audience)) {
            return bffProperties.getExternal().requireAudience(audience).getClientId().trim();
        }
        if (audience != null && bffProperties.getQr().getChannels().containsKey(audience)) {
            return bffProperties.getQr().requireClientId(audience);
        }
        return bffProperties.getClientId();
    }

    private UserAccount resolveAccount(Long personId, String designatedUsername, String openId) {
        if (personId == null) {
            throw UnboundAccount.exception(openId, designatedUsername);
        }
        if (designatedUsername != null && !designatedUsername.isBlank()) {
            PrincipalDirectory directory = principalDirectory.getIfAvailable();
            if (directory == null) {
                throw new PlatformException("未启用多账号");
            }
            return directory.resolveDesignated(personId, designatedUsername.trim())
                    .orElseThrow(() -> new PlatformException("指定账号无效"));
        }
        List<UserAccount> accounts = userAccountService.findEnabledByPersonId(personId);
        if (accounts == null || accounts.isEmpty()) {
            throw UnboundAccount.exception(openId, designatedUsername);
        }
        return accounts.get(0);
    }
}
