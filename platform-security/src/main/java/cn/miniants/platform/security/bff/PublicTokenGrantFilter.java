package cn.miniants.platform.security.bff;

import cn.miniants.platform.security.LoginAttempt;
import cn.miniants.platform.security.LoginAudits;
import cn.miniants.platform.security.SecurityAuditSink;
import cn.miniants.platform.security.sas.PlatformSasProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 公开 {@code /oauth/token} 拒绝 BFF-only grant，并记录 client_credentials / refresh 成败。
 * 进程内换票不走此 Filter。
 */
public class PublicTokenGrantFilter extends OncePerRequestFilter {

    private static final String BODY =
            "{\"error\":\"unauthorized_client\",\"error_description\":\"请使用登录门面换票\"}";

    private final Set<String> deniedGrants;
    private final SecurityAuditSink auditSink;

    public PublicTokenGrantFilter(PlatformSasProperties properties) {
        this(properties, null);
    }

    public PublicTokenGrantFilter(PlatformSasProperties properties, SecurityAuditSink auditSink) {
        Set<String> denied = new LinkedHashSet<>();
        if (properties.getPublicDeniedGrants() != null) {
            for (String grant : properties.getPublicDeniedGrants()) {
                if (grant != null && !grant.isBlank()) {
                    denied.add(grant.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        this.deniedGrants = Set.copyOf(denied);
        this.auditSink = auditSink;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String grant = request.getParameter("grant_type");
        String normalized = grant == null ? null : grant.trim().toLowerCase(Locale.ROOT);
        String clientId = LoginAudits.clientIdOf(request);
        if (normalized != null && deniedGrants.contains(normalized)) {
            LoginAudits.record(auditSink, request, LoginAttempt.of(
                    false, null, clientId, normalized, "请使用登录门面换票"));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(BODY);
            return;
        }
        filterChain.doFilter(request, response);
        if (LoginAudits.CLIENT_CREDENTIALS.equals(normalized) || LoginAudits.REFRESH.equals(normalized)) {
            boolean ok = response.getStatus() < 400;
            String recordedGrant = !ok && response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED
                    && LoginAudits.CLIENT_CREDENTIALS.equals(normalized)
                    ? LoginAudits.CLIENT_SECRET
                    : normalized;
            String message = ok ? null : "客户端鉴权失败";
            if (LoginAudits.REFRESH.equals(normalized) && !ok) {
                message = "登录已失效，请重新登录";
            }
            LoginAudits.record(auditSink, request, LoginAttempt.of(ok, null, clientId, recordedGrant, message));
        }
    }
}
