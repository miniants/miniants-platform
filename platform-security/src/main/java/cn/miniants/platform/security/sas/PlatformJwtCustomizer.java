package cn.miniants.platform.security.sas;

import cn.miniants.platform.security.account.UserAccount;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

public class PlatformJwtCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    @Override
    public void customize(JwtEncodingContext context) {
        UserAccount user = resolve(context);
        if (user == null) {
            return;
        }
        String name = user.displayName();
        if (name == null || name.isBlank()) {
            name = user.username();
        }
        context.getClaims()
                .claim("userId", user.id())
                .claim("username", user.username())
                .claim("name", name)
                .claim("sysAdmin", user.sysAdmin())
                .claim("roleIds", user.roleIds());
    }

    /** 供采用方 {@link OAuth2TokenCustomizer} 复用的 UserAccount 解析入口。 */
    public static UserAccount resolveUserAccount(JwtEncodingContext context) {
        if (context.getAuthorizationGrant() instanceof ResolvedAccountGrant grant
                && grant.userAccount() != null) {
            return grant.userAccount();
        }
        if (context.getAuthorizationGrant() instanceof PasswordGrantAuthenticationToken grant
                && grant.userAccount() != null) {
            return grant.userAccount();
        }
        if (context.getPrincipal() instanceof AbstractAuthenticationToken authentication
                && authentication.getDetails() instanceof UserAccount account) {
            return account;
        }
        OAuth2Authorization authorization = context.getAuthorization();
        if (authorization != null) {
            Object attr = authorization.getAttribute(UserAccount.ATTR);
            if (attr instanceof UserAccount account) {
                return account;
            }
        }
        return null;
    }

    private static UserAccount resolve(JwtEncodingContext context) {
        return resolveUserAccount(context);
    }
}
