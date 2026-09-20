package cn.miniants.platform.core.json;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 兼容 {@code yyyy-MM-dd HH:mm}、{@code yyyy-MM-dd HH:mm:ss} 与 ISO_LOCAL_DATE_TIME。
 */
public final class FlexibleLocalDateTimeDeserializer extends ValueDeserializer<LocalDateTime> {

    public static final FlexibleLocalDateTimeDeserializer INSTANCE = new FlexibleLocalDateTimeDeserializer();

    private FlexibleLocalDateTimeDeserializer() {
    }

    @Override
    public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) {
        String text = parser.getString();
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.trim();
        for (DateTimeFormatter format : PlatformDateTimeFormats.LOCAL_DATE_TIME_READ) {
            try {
                return LocalDateTime.parse(value, format);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        return (LocalDateTime) context.handleWeirdStringValue(LocalDateTime.class, value,
                "期望 yyyy-MM-dd HH:mm[:ss] 或 ISO_LOCAL_DATE_TIME");
    }
}
