package cn.miniants.platform.security;

import cn.miniants.platform.core.error.PlatformException;
import cn.miniants.platform.security.account.InMemoryUserAccountService;
import cn.miniants.platform.security.account.UserAccount;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordChangeServiceTest {

    private final PasswordEncoder encoder = new DelegatingPasswordEncoder(
            "bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder()));
    private final InMemoryUserAccountService accounts = new InMemoryUserAccountService()
            .add(new UserAccount(0L, "root", encoder.encode("oldpass"), "系统", true, true, List.of(), null));
    private final PasswordChangeService service = new PasswordChangeService(accounts, encoder);

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void changesPasswordForIdZero() {
        bind(CurrentUser.user(0L, "root").sysAdmin(true).build());
        PasswordChange body = new PasswordChange();
        body.setOldPassword("oldpass");
        body.setNewPassword("newpass");
        service.change(body);
        assertTrue(encoder.matches("newpass", accounts.findById(0L).orElseThrow().passwordHash()));
    }

    @Test
    void rejectsWrongOldPassword() {
        bind(CurrentUser.user(0L, "root").build());
        PasswordChange body = new PasswordChange();
        body.setOldPassword("bad");
        body.setNewPassword("newpass");
        assertThrows(PlatformException.class, () -> service.change(body));
        assertTrue(encoder.matches("oldpass", accounts.findById(0L).orElseThrow().passwordHash()));
    }

    @Test
    void rejectsClientActor() {
        bind(CurrentUser.client("device").build());
        PasswordChange body = new PasswordChange();
        body.setOldPassword("oldpass");
        body.setNewPassword("newpass");
        assertThrows(PlatformException.class, () -> service.change(body));
    }

    private static void bind(CurrentUser user) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        CurrentUser.bind(request, user);
    }
}
