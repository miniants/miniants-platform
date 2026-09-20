package cn.miniants.platform.security;

import java.util.List;
import java.util.Set;

public record CurrentUserView(
        String actor,
        Long userId,
        String username,
        String name,
        String clientId,
        boolean sysAdmin,
        List<Long> roleIds,
        Set<String> permissions,
        Set<String> scopes,
        Object menus
) {

    public static CurrentUserView from(CurrentUser user) {
        return from(user, null);
    }

    public static CurrentUserView from(CurrentUser user, Object menus) {
        return new CurrentUserView(
                user.actor().wire(),
                user.userId(),
                user.username(),
                user.name(),
                user.clientId(),
                user.sysAdmin(),
                user.roleIds(),
                user.permissions(),
                user.scopes(),
                menus);
    }
}
