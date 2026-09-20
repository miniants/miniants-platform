package cn.miniants.platform.security.bff;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TokenResponseMapsTest {

    @Test
    void copiesTokenCustomizerClaimsAndSkipsProtocolKeys() {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"B1\",\"iss\":\"http://jwy\",\"real_auth\":\"AUTHENTICATED\",\"username\":\"B1\",\"bizPersona\":\"teacher\",\"openId\":\"oid\"}"
                        .getBytes(StandardCharsets.UTF_8));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", "hdr." + payload + ".sig");
        body.put("scope", "miniapp");
        TokenResponseMaps.copyCustomizerClaims(body, "hdr." + payload + ".sig");
        assertEquals("AUTHENTICATED", body.get("real_auth"));
        assertEquals("B1", body.get("username"));
        assertEquals("teacher", body.get("bizPersona"));
        assertEquals("oid", body.get("openId"));
        assertFalse(body.containsKey("sub"));
        assertFalse(body.containsKey("iss"));
        assertEquals("miniapp", body.get("scope"));
    }
}
