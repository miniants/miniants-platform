package cn.miniants.platform.security.sas;

import cn.miniants.platform.security.account.UserAccount;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Collections;
import java.util.Set;

public class PasswordGrantAuthenticationToken extends AbstractAuthenticationToken {

    private final Authentication clientPrincipal;
    private final String username;
    private final String password;
    private final Set<String> scopes;
    private UserAccount userAccount;

    public PasswordGrantAuthenticationToken(Authentication clientPrincipal, String username,
                                            String password, Set<String> scopes) {
        super(Collections.emptyList());
        this.clientPrincipal = clientPrincipal;
        this.username = username;
        this.password = password;
        this.scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public Set<String> getScopes() {
        return scopes;
    }

    public UserAccount userAccount() {
        return userAccount;
    }

    public void attach(UserAccount userAccount) {
        this.userAccount = userAccount;
    }

    @Override
    public Object getCredentials() {
        return password;
    }

    @Override
    public Object getPrincipal() {
        return clientPrincipal;
    }
}
