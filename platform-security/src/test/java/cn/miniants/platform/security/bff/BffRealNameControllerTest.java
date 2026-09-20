package cn.miniants.platform.security.bff;

import cn.miniants.platform.core.error.PlatformException;
import cn.miniants.platform.security.AccessAuth;
import cn.miniants.platform.security.RequestSecurityAuditSink;
import cn.miniants.platform.security.account.UserAccount;
import cn.miniants.platform.security.account.UserAccountService;
import cn.miniants.platform.security.crypto.IdCredentialHasher;
import cn.miniants.platform.security.identity.ExternalIdentity;
import cn.miniants.platform.security.identity.ExternalIdentityBinding;
import cn.miniants.platform.security.identity.ExternalIdentityProvider;
import cn.miniants.platform.security.identity.ExternalIdentityProviderRegistry;
import cn.miniants.platform.security.person.Person;
import cn.miniants.platform.security.person.PersonExpansionSource;
import cn.miniants.platform.security.person.PersonRegistry;
import cn.miniants.platform.security.person.PrincipalDirectory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BffRealNameControllerTest {

    @Test
    void successStampsLoginOk() {
        BffRealNameController controller = controller(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/open/id-no");

        Map<String, Object> body = controller.bindAndIssue(
                "wx", null, "110101199001011234", "张三", null, null, request);

        assertEquals("tok", body.get("access_token"));
        assertEquals("ok", AccessAuth.loginOf(request));
        assertEquals("身份证实名登录", AccessAuth.opOf(request));
    }

    @Test
    void rejectStampsLoginFail() {
        BffRealNameController controller = controller(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/open/id-no");

        assertThrows(PlatformException.class, () -> controller.bindAndIssue(
                null, null, "110101199001011234", "张三", null, null, request));

        assertEquals("fail", AccessAuth.loginOf(request));
        assertEquals("身份证实名登录", AccessAuth.opOf(request));
    }

    @SuppressWarnings("unchecked")
    private static BffRealNameController controller(boolean resolveWx) {
        PlatformBffProperties properties = new PlatformBffProperties();
        properties.setClientId("demo");
        ExternalIdentityProvider identities = mock(ExternalIdentityProvider.class);
        when(identities.id()).thenReturn("wx");
        if (resolveWx) {
            when(identities.resolve(any())).thenReturn(new ExternalIdentity("wx", "oid", 1L));
        } else {
            when(identities.resolve(any())).thenThrow(new PlatformException("微信登录码无效"));
        }
        ExternalIdentityProviderRegistry registry =
                new ExternalIdentityProviderRegistry(List.of(identities));
        IdCredentialHasher hasher = mock(IdCredentialHasher.class);
        when(hasher.digest(anyString(), anyString())).thenReturn("digest");
        PersonRegistry persons = mock(PersonRegistry.class);
        when(persons.verifyOrCreate(anyString(), anyString(), anyString(), isNull()))
                .thenReturn(new Person(1L, "张三", "id_card", "digest", null));
        ExternalIdentityBinding binding = mock(ExternalIdentityBinding.class);
        UserAccountService accounts = mock(UserAccountService.class);
        when(accounts.findEnabledByPersonId(1L)).thenReturn(List.of(
                new UserAccount(9L, "alice", "hash", "张三", true, false, List.of(), 1L)));
        UserTokenIssuer tokens = mock(UserTokenIssuer.class);
        when(tokens.issueForAccount(anyString(), any())).thenReturn(Map.of("access_token", "tok"));
        ObjectProvider<PrincipalDirectory> directory = mock(ObjectProvider.class);
        when(directory.getIfAvailable()).thenReturn(null);
        ObjectProvider<PersonExpansionSource> expansion = mock(ObjectProvider.class);
        when(expansion.getIfAvailable()).thenReturn(null);
        return new BffRealNameController(
                properties, registry, hasher, persons, binding, accounts,
                directory, expansion, tokens, new RequestSecurityAuditSink());
    }
}
