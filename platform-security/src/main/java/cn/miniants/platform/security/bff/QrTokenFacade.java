package cn.miniants.platform.security.bff;

import cn.miniants.platform.core.error.PlatformException;
import cn.miniants.platform.security.CurrentUser;
import cn.miniants.platform.security.LoginAttempt;
import cn.miniants.platform.security.LoginAudits;
import cn.miniants.platform.security.SecurityAuditSink;
import cn.miniants.platform.security.UnboundAccount;
import cn.miniants.platform.security.account.UserAccount;
import cn.miniants.platform.security.account.UserAccountService;
import cn.miniants.platform.security.identity.ExternalIdentity;
import cn.miniants.platform.security.identity.ExternalIdentityBinding;
import cn.miniants.platform.security.person.PrincipalDirectory;
import cn.miniants.platform.security.qr.QrLoginSession;
import cn.miniants.platform.security.qr.QrLoginStatus;
import cn.miniants.platform.security.qr.QrLoginStore;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

/**
 * 扫码 SUCCESS 后按外部身份找 person → 账号 → 出票。
 */
public class QrTokenFacade {

    private final QrLoginStore qrLoginStore;
    private final ExternalIdentityBinding binding;
    private final UserAccountService userAccountService;
    private final ObjectProvider<PrincipalDirectory> principalDirectory;
    private final UserTokenIssuer tokenIssuer;
    private final ObjectProvider<SecurityAuditSink> auditSink;

    public QrTokenFacade(
            QrLoginStore qrLoginStore,
            ExternalIdentityBinding binding,
            UserAccountService userAccountService,
            ObjectProvider<PrincipalDirectory> principalDirectory,
            UserTokenIssuer tokenIssuer) {
        this(qrLoginStore, binding, userAccountService, principalDirectory, tokenIssuer, null);
    }

    public QrTokenFacade(
            QrLoginStore qrLoginStore,
            ExternalIdentityBinding binding,
            UserAccountService userAccountService,
            ObjectProvider<PrincipalDirectory> principalDirectory,
            UserTokenIssuer tokenIssuer,
            ObjectProvider<SecurityAuditSink> auditSink) {
        this.qrLoginStore = qrLoginStore;
        this.binding = binding;
        this.userAccountService = userAccountService;
        this.principalDirectory = principalDirectory;
        this.tokenIssuer = tokenIssuer;
        this.auditSink = auditSink;
    }

    public Map<String, Object> queryAndIssue(String scene) {
        QrLoginSession session = qrLoginStore.find(scene)
                .orElseThrow(() -> new PlatformException("二维码已失效"));
        if (session.status() != QrLoginStatus.SUCCESS) {
            return Map.of("scene", session.scene(), "status", session.status().name());
        }
        String clientId = session.initiatingClientId();
        boolean issued = false;
        try {
            if (session.provider() == null || session.provider().isBlank()
                    || session.subject() == null || session.subject().isBlank()) {
                throw UnboundAccount.exception(session.subject(), session.designatedUsername());
            }
            ExternalIdentity identity = binding.find(session.provider(), session.subject())
                    .orElseThrow(() -> UnboundAccount.exception(session.subject(), session.designatedUsername()));
            UserAccount account = resolveAccount(
                    identity.personId(), session.designatedUsername(), session.subject());
            issued = true;
            Map<String, Object> token = tokenIssuer.issueForAccount(clientId, account, LoginAudits.QR);
            qrLoginStore.remove(scene);
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("scene", session.scene());
            body.put("status", QrLoginStatus.SUCCESS.name());
            body.put("jwtData", token);
            return body;
        } catch (RuntimeException ex) {
            if (!issued) {
                SecurityAuditSink sink = auditSink == null ? null : auditSink.getIfAvailable();
                LoginAudits.record(sink, CurrentUser.currentRequest(),
                        LoginAttempt.of(false, null, clientId, LoginAudits.QR, ex.getMessage()));
            }
            throw ex;
        }
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
