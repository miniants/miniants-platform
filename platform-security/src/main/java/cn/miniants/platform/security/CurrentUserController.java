package cn.miniants.platform.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CurrentUserController {

    private final ObjectProvider<CurrentUserMenuLoader> menuLoader;

    public CurrentUserController(ObjectProvider<CurrentUserMenuLoader> menuLoader) {
        this.menuLoader = menuLoader;
    }

    @Authenticated
    @GetMapping("/auth/me")
    public CurrentUserView me() {
        CurrentUser user = CurrentUser.require();
        CurrentUserMenuLoader loader = menuLoader.getIfAvailable();
        return CurrentUserView.from(user, loader == null ? null : loader.load(user));
    }
}
