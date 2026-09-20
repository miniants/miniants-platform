package cn.miniants.platform.security;

import cn.miniants.platform.core.error.PlatformException;
import cn.miniants.platform.security.account.UserAccount;
import cn.miniants.platform.security.account.UserAccountService;
import org.springframework.security.crypto.password.PasswordEncoder;

public class PasswordChangeService {

    private final UserAccountService userAccountService;
    private final PasswordEncoder passwordEncoder;

    public PasswordChangeService(UserAccountService userAccountService, PasswordEncoder passwordEncoder) {
        this.userAccountService = userAccountService;
        this.passwordEncoder = passwordEncoder;
    }

    public void change(PasswordChange body) {
        CurrentUser user = CurrentUser.require();
        if (user.actor() != Actor.USER || user.userId() == null) {
            throw new PlatformException("当前账号不能修改密码");
        }
        if (body == null || isBlank(body.getOldPassword()) || isBlank(body.getNewPassword())) {
            throw new PlatformException("原密码和新密码不能为空");
        }
        String oldPassword = body.getOldPassword();
        String newPassword = body.getNewPassword().trim();
        if (newPassword.length() < 6) {
            throw new PlatformException("新密码至少 6 位");
        }
        UserAccount account = userAccountService.findById(user.userId())
                .or(() -> userAccountService.findByUsername(user.username()))
                .orElseThrow(() -> new PlatformException("用户不存在"));
        if (!passwordEncoder.matches(oldPassword, account.passwordHash())) {
            throw new PlatformException("原密码不正确");
        }
        if (passwordEncoder.matches(newPassword, account.passwordHash())) {
            throw new PlatformException("新密码不能与原密码相同");
        }
        userAccountService.updatePasswordHash(account.id(), passwordEncoder.encode(newPassword));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
