package cn.miniants.platform.security;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PasswordChangeController {

    private final PasswordChangeService passwordChangeService;

    public PasswordChangeController(PasswordChangeService passwordChangeService) {
        this.passwordChangeService = passwordChangeService;
    }

    @Authenticated
    @OperLog(value = "修改密码", eventType = OperLogEventTypes.PASSWORD_CHANGE)
    @PostMapping("/auth/password")
    public Boolean change(@RequestBody PasswordChange body) {
        passwordChangeService.change(body);
        return Boolean.TRUE;
    }
}
