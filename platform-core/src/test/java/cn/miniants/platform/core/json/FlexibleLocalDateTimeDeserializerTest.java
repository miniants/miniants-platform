package cn.miniants.platform.core.json;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FlexibleLocalDateTimeDeserializerTest {

    @Test
    void parsesWithAndWithoutSeconds() {
        SimpleModule module = new SimpleModule("platform-datetime");
        module.addDeserializer(LocalDateTime.class, FlexibleLocalDateTimeDeserializer.INSTANCE);
        var mapper = JsonMapper.builder().addModule(module).build();

        assertEquals(LocalDateTime.of(2026, 8, 20, 12, 16, 0),
                mapper.readValue("\"2026-08-20 12:16\"", LocalDateTime.class));
        assertEquals(LocalDateTime.of(2026, 8, 20, 12, 16, 30),
                mapper.readValue("\"2026-08-20 12:16:30\"", LocalDateTime.class));
        assertNotNull(mapper.readValue("\"2026-08-20T12:16:30\"", LocalDateTime.class));
    }
}
