package cn.miniants.platform.security.bff;

import cn.miniants.platform.core.error.PlatformException;
import cn.miniants.platform.security.LoginAttempt;
import cn.miniants.platform.security.LoginAudits;
import cn.miniants.platform.security.PublicAccess;
import cn.miniants.platform.security.SecurityAuditSink;
import cn.miniants.platform.security.challenge.LoginChallenge;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@PublicAccess
@RequestMapping("/auth/open/web")
public class BffWebController {

    private final UserTokenIssuer userTokenIssuer;
    private final PlatformBffProperties properties;
    private final ObjectProvider<SecurityAuditSink> auditSink;
    private final ObjectProvider<LoginChallenge> loginChallenge;

    public BffWebController(
            UserTokenIssuer userTokenIssuer,
            PlatformBffProperties properties,
            ObjectProvider<SecurityAuditSink> auditSink,
            ObjectProvider<LoginChallenge> loginChallenge) {
        this.userTokenIssuer = userTokenIssuer;
        this.properties = properties;
        this.auditSink = auditSink;
        this.loginChallenge = loginChallenge;
    }

    @PostMapping("/password")
    public Map<String, Object> password(
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            @RequestParam(value = "designated_username", required = false) String designatedUsername,
            @RequestParam(value = "captchaId", required = false) String captchaId,
            @RequestParam(value = "offsetX", required = false) String offsetX,
            HttpServletRequest request) {
        String clientId = properties.getClientId();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            recordPreIssue(request, username, clientId, "用户名或密码错误");
            throw new PlatformException("用户名或密码错误");
        }
        String trimmed = username.trim();
        String clientKey = clientKey(request, properties.getChallenge().isTrustForwardedFor());
        LoginChallenge challenge = challengeOrNull();
        try {
            if (challenge != null && challenge.requiresChallenge(clientKey)) {
                Map<String, String> params = new HashMap<>();
                if (captchaId != null) {
                    params.put("captchaId", captchaId);
                }
                if (offsetX != null) {
                    params.put("offsetX", offsetX);
                }
                challenge.assertPassed(clientKey, params);
            }
        } catch (RuntimeException ex) {
            if (challenge != null) {
                challenge.recordFailure(clientKey);
            }
            recordPreIssue(request, trimmed, clientId, ex.getMessage());
            throw ex;
        }
        try {
            Map<String, Object> token = userTokenIssuer.issuePassword(
                    clientId, trimmed, password, designatedUsername);
            if (challenge != null) {
                challenge.clear(clientKey);
            }
            return token;
        } catch (RuntimeException ex) {
            if (challenge != null) {
                challenge.recordFailure(clientKey);
            }
            throw ex;
        }
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh(
            @RequestParam("refresh_token") String refreshToken,
            HttpServletRequest request) {
        String clientId = properties.getClientId();
        if (refreshToken == null || refreshToken.isBlank()) {
            LoginAudits.record(auditSink.getIfAvailable(), request,
                    LoginAttempt.of(false, null, clientId, LoginAudits.REFRESH, "登录已失效，请重新登录"));
            throw new PlatformException("登录已失效，请重新登录");
        }
        return userTokenIssuer.issueRefresh(clientId, refreshToken.trim());
    }

    private LoginChallenge challengeOrNull() {
        if (!properties.getChallenge().isEnabled()) {
            return null;
        }
        return loginChallenge.getIfAvailable();
    }

    static String clientKey(HttpServletRequest request, boolean trustForwardedFor) {
        if (request == null) {
            return "unknown";
        }
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private void recordPreIssue(HttpServletRequest request, String username, String clientId, String message) {
        LoginAudits.record(auditSink.getIfAvailable(), request,
                LoginAttempt.of(false, username, clientId, LoginAudits.PASSWORD, message));
    }
}
