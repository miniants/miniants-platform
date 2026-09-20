package cn.miniants.platform.data.entity;

import cn.miniants.platform.core.json.PlatformJacksonAutoConfiguration;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseEntityJsonFormatTest {

    @Test
    void auditTimesSerializeToMinutes() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new PlatformJacksonAutoConfiguration().platformDateTimeCustomizer().customize(builder);
        ObjectMapper mapper = builder.build();

        BaseEntity entity = new BaseEntity();
        entity.setCreateTime(LocalDateTime.of(2026, 8, 25, 12, 16, 30));
        entity.setUpdateTime(LocalDateTime.of(2026, 8, 25, 13, 1, 9));

        String json = mapper.writeValueAsString(entity);
        assertTrue(json.contains("\"createTime\":\"2026-08-25 12:16\""), json);
        assertTrue(json.contains("\"updateTime\":\"2026-08-25 13:01\""), json);
        assertTrue(!json.contains("12:16:30"), json);
    }
}
