package cn.miniants.platform.security.account;

import java.util.ArrayList;

/**
 * 供 SAS JDBC 持久化使用的 {@link UserAccount} 副本：避免 {@code List.of()} 等不可变集合的 Jackson 类型 id 被拒。
 */
public final class UserAccounts {

    private UserAccounts() {
    }

    public static UserAccount forAuthorizationStore(UserAccount user) {
        if (user == null) {
            return null;
        }
        return new UserAccount(
                user.id(),
                user.username(),
                user.passwordHash(),
                user.displayName(),
                user.enabled(),
                user.sysAdmin(),
                new ArrayList<>(user.roleIds()),
                user.personId());
    }
}
