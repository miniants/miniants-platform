package cn.miniants.platform.security;

import cn.miniants.platform.data.audit.Auditor;
import cn.miniants.platform.data.audit.AuditorSupplier;

import java.util.Optional;

/**
 * 实体审计人取登录名 {@link CurrentUser#username()}，不写显示名（显示名会变、也不唯一）。
 * 没绑定用户时交给 {@link Auditor#system()}。
 */
public class CurrentUserAuditorSupplier implements AuditorSupplier {

    @Override
    public Optional<Auditor> current() {
        CurrentUser user = CurrentUser.find();
        if (user == null || user.actor() != Actor.USER) {
            return Optional.empty();
        }
        String username = user.username();
        return Optional.of(new Auditor(
                user.userId() == null ? -1L : user.userId(),
                username == null || username.isBlank() ? "system" : username));
    }
}
