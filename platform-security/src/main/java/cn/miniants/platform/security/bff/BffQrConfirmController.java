package cn.miniants.platform.security.bff;

import cn.miniants.platform.core.error.PlatformException;
import cn.miniants.platform.security.Authenticated;
import cn.miniants.platform.security.CurrentUser;
import cn.miniants.platform.security.LoginAttempt;
import cn.miniants.platform.security.LoginAudits;
import cn.miniants.platform.security.PublicAccess;
import cn.miniants.platform.security.SecurityAuditSink;
import cn.miniants.platform.security.UnboundAccount;
import cn.miniants.platform.security.account.UserAccount;
import cn.miniants.platform.security.account.UserAccountService;
import cn.miniants.platform.security.identity.ExternalIdentity;
import cn.miniants.platform.security.identity.ExternalIdentityBinding;
import cn.miniants.platform.security.qr.QrLoginSession;
import cn.miniants.platform.security.qr.QrLoginStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth/open/{channel}/qrcode")
public class BffQrConfirmController {

    private final PlatformBffProperties properties;
    private final QrLoginStore qrLoginStore;
    private final UserAccountService userAccountService;
    private final ExternalIdentityBinding binding;
    private final ObjectProvider<SecurityAuditSink> auditSink;

    public BffQrConfirmController(
            PlatformBffProperties properties,
            QrLoginStore qrLoginStore,
            UserAccountService userAccountService,
            ExternalIdentityBinding binding,
            ObjectProvider<SecurityAuditSink> auditSink) {
        this.properties = properties;
        this.qrLoginStore = qrLoginStore;
        this.userAccountService = userAccountService;
        this.binding = binding;
        this.auditSink = auditSink;
    }

    /**
     * 已发小程序在 wxLogin 之前只传 scene。此处只把 INIT 标成 SCANNED，不换微信码。
     */
    @PublicAccess
    @PostMapping("/scanned")
    public Map<String, Object> scanned(
            @PathVariable("channel") String channel,
            @RequestParam("scene") String scene) {
        try {
            requireConfirmChannel(channel);
            QrLoginSession session = qrLoginStore.markScanned(scene, null, null);
            return statusBody(session);
        } catch (RuntimeException ex) {
            recordQrFail(null, ex.getMessage());
            throw ex;
        }
    }

    /**
     * 确认登录。openid 只从已登录账号的外部身份绑定读（与 JWT {@code openId} 同源），
     * 不接受 body {@code wx_code} / {@code openId}。
     */
    @Authenticated
    @PostMapping("/auth-pass")
    public Map<String, Object> authPass(
            @PathVariable("channel") String channel,
            @RequestParam("scene") String scene,
            @RequestParam(value = "designated_username", required = false) String designatedUsername,
            @RequestParam(value = "designatedUsername", required = false) String designatedUsernameCamel) {
        String username = null;
        try {
            requireConfirmChannel(channel);
            CurrentUser user = CurrentUser.require();
            username = user.username();
            UserAccount account = userAccountService.findById(user.userId())
                    .or(() -> user.username() == null || user.username().isBlank()
                            ? Optional.empty()
                            : userAccountService.findByUsername(user.username()))
                    .orElseThrow(() -> UnboundAccount.exception(null, user.username()));
            if (account.personId() == null) {
                throw UnboundAccount.exception(null, account.username());
            }
            String provider = properties.getQr().requireConfirmProvider();
            ExternalIdentity identity = binding.findByPerson(provider, account.personId())
                    .orElseThrow(() -> UnboundAccount.exception(null, account.username()));
            QrLoginSession session = qrLoginStore.markSuccess(
                    scene,
                    identity.provider(),
                    identity.subject(),
                    firstText(designatedUsername, designatedUsernameCamel));
            return statusBody(session);
        } catch (RuntimeException ex) {
            recordQrFail(username, ex.getMessage());
            throw ex;
        }
    }

    private void requireConfirmChannel(String channel) {
        String confirm = properties.getQr().getConfirmChannel();
        if (confirm == null || !confirm.equals(channel)) {
            throw new PlatformException("非法扫码确认通道");
        }
        properties.getQr().requireClientId(channel);
    }

    private static Map<String, Object> statusBody(QrLoginSession session) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scene", session.scene());
        body.put("status", session.status().name());
        return body;
    }

    private void recordQrFail(String username, String message) {
        String clientId = properties.getQr().getChannels().containsKey("miniapp")
                ? properties.getQr().requireClientId("miniapp")
                : properties.getClientId();
        LoginAudits.record(auditSink == null ? null : auditSink.getIfAvailable(), CurrentUser.currentRequest(),
                LoginAttempt.of(false, username, clientId, LoginAudits.QR, message));
    }

    private static String firstText(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }
}
