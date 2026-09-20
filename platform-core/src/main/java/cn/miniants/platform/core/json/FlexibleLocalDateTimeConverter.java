package cn.miniants.platform.core.json;

import org.springframework.core.convert.converter.Converter;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 表单 / query 把中文日期字符串转成 {@link LocalDateTime}。空串当 null。
 */
public final class FlexibleLocalDateTimeConverter implements Converter<String, LocalDateTime> {

    public static final FlexibleLocalDateTimeConverter INSTANCE = new FlexibleLocalDateTimeConverter();

    private FlexibleLocalDateTimeConverter() {
    }

    @Override
    @Nullable
    public LocalDateTime convert(@Nullable String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        String value = source.trim();
        for (DateTimeFormatter format : PlatformDateTimeFormats.LOCAL_DATE_TIME_READ) {
            try {
                return LocalDateTime.parse(value, format);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        throw new IllegalArgumentException("期望 yyyy-MM-dd HH:mm[:ss] 或 ISO_LOCAL_DATE_TIME");
    }
}
