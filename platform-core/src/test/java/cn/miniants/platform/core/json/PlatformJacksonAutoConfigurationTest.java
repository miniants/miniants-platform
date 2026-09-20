package cn.miniants.platform.core.json;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformJacksonAutoConfigurationTest {

    private static ObjectMapper mapper() {
        JsonMapper.Builder builder = JsonMapper.builder();
        PlatformJacksonAutoConfiguration config = new PlatformJacksonAutoConfiguration();
        config.platformJsSafeLongCustomizer().customize(builder);
        config.platformDateTimeCustomizer().customize(builder);
        return builder.build();
    }

    @Test
    void writesLocalDateTimeAsChineseDateTime() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("at", LocalDateTime.of(2026, 8, 25, 12, 16, 30));

        assertEquals("{\"at\":\"2026-08-25 12:16:30\"}", mapper().writeValueAsString(payload));
    }

    @Test
    void readsFlexibleLocalDateTime() {
        ObjectMapper objectMapper = mapper();
        assertEquals(LocalDateTime.of(2026, 8, 25, 12, 16, 0),
                objectMapper.readValue("\"2026-08-25 12:16\"", LocalDateTime.class));
        assertEquals(LocalDateTime.of(2026, 8, 25, 12, 16, 30),
                objectMapper.readValue("\"2026-08-25 12:16:30\"", LocalDateTime.class));
    }

    @Test
    void stillKeepsSafeLongAsNumber() {
        String json = mapper().writeValueAsString(Map.of("code", 200L));
        assertTrue(json.contains("\"code\":200"), json);
    }
}
