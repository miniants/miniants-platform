package cn.miniants.platform.core.json;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsSafeLongSerializerTest {

    private static JsonMapper mapper() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new PlatformJacksonAutoConfiguration().platformJsSafeLongCustomizer().customize(builder);
        return builder.build();
    }

    @Test
    void keepsSafeLongAsNumberAndUnsafeAsString() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", 200L);
        payload.put("sysAdminId", 0L);
        payload.put("total", 15L);
        payload.put("id", 1834567890123456789L);
        payload.put("roleIds", List.of(1200000000000000001L, 1200000000000012L));

        String json = mapper().writeValueAsString(payload);

        assertTrue(json.contains("\"code\":200"), json);
        assertFalse(json.contains("\"code\":\"200\""), json);
        assertTrue(json.contains("\"sysAdminId\":0"), json);
        assertTrue(json.contains("\"total\":15"), json);
        assertTrue(json.contains("\"id\":\"1834567890123456789\""), json);
        assertTrue(json.contains("\"1200000000000000001\""), json);
        assertTrue(json.contains("1200000000000012"), json);
        assertFalse(json.contains("\"1200000000000012\""), json);
    }

    @Test
    void boundaryValuesStayOnTheCorrectSide() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("atMax", JsSafeLongSerializer.JS_MAX_SAFE);
        payload.put("overMax", JsSafeLongSerializer.JS_MAX_SAFE + 1);
        payload.put("atMin", -JsSafeLongSerializer.JS_MAX_SAFE);
        payload.put("underMin", -JsSafeLongSerializer.JS_MAX_SAFE - 1);

        String json = mapper().writeValueAsString(payload);

        assertTrue(json.contains("\"atMax\":9007199254740991"), json);
        assertTrue(json.contains("\"overMax\":\"9007199254740992\""), json);
        assertTrue(json.contains("\"atMin\":-9007199254740991"), json);
        assertTrue(json.contains("\"underMin\":\"-9007199254740992\""), json);
    }

    @Test
    void inputStillAcceptsBothNumberAndString() {
        record Probe(Long id, long total, Long code) {
        }

        Probe probe = mapper().readValue(
                "{\"id\":\"1834567890123456789\",\"total\":\"42\",\"code\":200}", Probe.class);

        assertEquals(1834567890123456789L, probe.id());
        assertEquals(42L, probe.total());
        assertEquals(200L, probe.code());
    }

    @Test
    void nullStaysNull() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", null);

        assertEquals("{\"id\":null}", mapper().writeValueAsString(payload));
    }
}
