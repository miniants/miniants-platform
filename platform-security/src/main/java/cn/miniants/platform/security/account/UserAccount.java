package cn.miniants.platform.security.account;

import java.util.List;

public record UserAccount(
        Long id,
        String username,
        String passwordHash,
        String displayName,
        boolean enabled,
        boolean sysAdmin,
        List<Long> roleIds,
        Long personId
) {
    public static final String ATTR = UserAccount.class.getName();

    public UserAccount {
        roleIds = roleIds == null ? List.of() : List.copyOf(roleIds);
    }
}
