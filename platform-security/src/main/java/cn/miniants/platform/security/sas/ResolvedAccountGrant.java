package cn.miniants.platform.security.sas;

import cn.miniants.platform.security.account.UserAccount;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.util.Collections;

/**
 * 已解析 {@link UserAccount} 的进程内 grant（微信 / USTB / 扫码等 {@link AccountTokenIssuer} 路径）。
 * JWT 定制器在首次出票时从此 grant 取账号，不依赖 principal.details 或尚未写入的 authorization 属性。
 */
public final class ResolvedAccountGrant extends AbstractAuthenticationToken {

    private final Authentication clientPrincipal;
    private final UserAccount userAccount;
    private final AuthorizationGrantType grantType;

    public ResolvedAccountGrant(Authentication clientPrincipal, UserAccount userAccount,
                                AuthorizationGrantType grantType) {
        super(Collections.emptyList());
        this.clientPrincipal = clientPrincipal;
        this.userAccount = userAccount;
        this.grantType = grantType;
        setAuthenticated(true);
    }

    public UserAccount userAccount() {
        return userAccount;
    }

    public AuthorizationGrantType grantType() {
        return grantType;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return clientPrincipal;
    }
}
