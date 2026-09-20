package cn.miniants.platform.security.bff;

import cn.miniants.platform.security.person.PrincipalSummary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class DefaultInternalTokenIssuerTest {

    @Test
    void attachPrincipalsIncludesSingleAccount() {
        Map<String, Object> body = Map.of("access_token", "t", "username", "M000000001");
        Map<String, Object> enriched = DefaultInternalTokenIssuer.attachPrincipals(
                body, List.of(new PrincipalSummary(1L, "M000000001", "测试生")));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> principals = (List<Map<String, Object>>) enriched.get("principals");
        assertEquals(1, principals.size());
        assertEquals("M000000001", principals.get(0).get("username"));
        assertEquals("测试生", principals.get(0).get("displayName"));
        assertEquals(1L, principals.get(0).get("accountId"));
        assertFalse(enriched.containsKey("individual"));
    }

    @Test
    void attachPrincipalsSkipsEmpty() {
        Map<String, Object> body = Map.of("access_token", "t");
        assertSame(body, DefaultInternalTokenIssuer.attachPrincipals(body, List.of()));
        assertSame(body, DefaultInternalTokenIssuer.attachPrincipals(body, null));
        assertFalse(body.containsKey("principals"));
    }
}
