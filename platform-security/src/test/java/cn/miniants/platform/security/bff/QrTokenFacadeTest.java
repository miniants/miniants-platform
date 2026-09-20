package cn.miniants.platform.security.bff;

import cn.miniants.platform.core.error.PlatformException;
import cn.miniants.platform.security.account.UserAccountService;
import cn.miniants.platform.security.identity.ExternalIdentityBinding;
import cn.miniants.platform.security.person.PrincipalDirectory;
import cn.miniants.platform.security.qr.QrLoginSession;
import cn.miniants.platform.security.qr.QrLoginStatus;
import cn.miniants.platform.security.qr.QrLoginStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QrTokenFacadeTest {

    @Test
    void unboundIncludesOpenIdTail() {
        QrLoginStore store = mock(QrLoginStore.class);
        ExternalIdentityBinding binding = mock(ExternalIdentityBinding.class);
        when(store.find("s1")).thenReturn(Optional.of(new QrLoginSession(
                "s1", QrLoginStatus.SUCCESS, "JWY_STU_KIOSK", "WX",
                "abcdefghijklmnopqrstuvwxyz12", null)));
        when(binding.find("WX", "abcdefghijklmnopqrstuvwxyz12")).thenReturn(Optional.empty());

        @SuppressWarnings("unchecked")
        ObjectProvider<PrincipalDirectory> directory = mock(ObjectProvider.class);
        QrTokenFacade facade = new QrTokenFacade(
                store, binding, mock(UserAccountService.class), directory, mock(UserTokenIssuer.class));

        PlatformException ex = assertThrows(PlatformException.class, () -> facade.queryAndIssue("s1"));
        assertEquals("未绑定账号（openid后8位=uvwxyz12）", ex.getMessage());
    }
}
