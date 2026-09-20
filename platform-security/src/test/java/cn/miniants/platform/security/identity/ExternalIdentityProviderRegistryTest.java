package cn.miniants.platform.security.identity;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExternalIdentityProviderRegistryTest {

    @Test
    void resolvesConfiguredProvider() {
        ExternalIdentityProvider wx = provider("WX");
        ExternalIdentityProviderRegistry registry = new ExternalIdentityProviderRegistry(List.of(wx));
        assertEquals(wx, registry.resolveConfigured("WX"));
    }

    @Test
    void singleProviderUsedWhenConfigBlank() {
        ExternalIdentityProvider wx = provider("WX");
        ExternalIdentityProviderRegistry registry = new ExternalIdentityProviderRegistry(List.of(wx));
        assertEquals(wx, registry.resolveConfigured(null));
    }

    @Test
    void rejectsDuplicateIds() {
        ExternalIdentityProvider a = provider("WX");
        ExternalIdentityProvider b = provider("WX");
        assertThrows(IllegalStateException.class,
                () -> new ExternalIdentityProviderRegistry(List.of(a, b)));
    }

    @Test
    void requiresExplicitProviderWhenMultiple() {
        ExternalIdentityProviderRegistry registry = new ExternalIdentityProviderRegistry(
                List.of(provider("A"), provider("B")));
        assertThrows(IllegalStateException.class, () -> registry.resolveConfigured(null));
    }

    private static ExternalIdentityProvider provider(String id) {
        ExternalIdentityProvider provider = mock(ExternalIdentityProvider.class);
        when(provider.id()).thenReturn(id);
        when(provider.resolve(Map.of())).thenReturn(new ExternalIdentity(id, "s", null));
        return provider;
    }
}
