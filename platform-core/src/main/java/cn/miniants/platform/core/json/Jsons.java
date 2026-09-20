package cn.miniants.platform.core.json;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * 薄封装共用 {@link ObjectMapper}（含 FlexibleLocalDateTime）。
 * 新代码优先注入 Spring {@code ObjectMapper}；本类供无注入点的静态调用（如 Feign 载荷工具）。
 */
public final class Jsons {

    private static final ObjectMapper MAPPER = createMapper();

    private Jsons() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String toJson(Object object) {
        if (object == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(object);
        } catch (JacksonException e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    public static JsonNode readTree(String json) {
        Objects.requireNonNull(json, "json is null");
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 解析失败", e);
        }
    }

    public static <T> T readValue(String json, Class<T> type) {
        if (json == null) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 反序列化失败", e);
        }
    }

    public static <T> T readValue(String json, TypeReference<T> type) {
        if (json == null) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 反序列化失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> readMap(String json) {
        return readValue(json, new TypeReference<Map<String, Object>>() {
        });
    }

    private static ObjectMapper createMapper() {
        SimpleModule timeModule = new SimpleModule("platform-datetime");
        timeModule.addDeserializer(LocalDateTime.class, FlexibleLocalDateTimeDeserializer.INSTANCE);
        timeModule.addSerializer(LocalDateTime.class,
                new PlatformLocalDateTimeSerializer(PlatformDateTimeFormats.DATE_TIME_FORMATTER));
        return JsonMapper.builder()
                .addModule(timeModule)
                .build();
    }
}
