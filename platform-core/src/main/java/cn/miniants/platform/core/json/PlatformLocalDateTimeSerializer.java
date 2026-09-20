package cn.miniants.platform.core.json;

import com.fasterxml.jackson.annotation.JsonFormat;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 默认写出 {@code yyyy-MM-dd HH:mm:ss}；字段上的 {@code @JsonFormat(pattern=…)} 覆盖格式
 * （审计字段到分）。不依赖 Jackson JavaTime 模块类名。
 */
public final class PlatformLocalDateTimeSerializer extends ValueSerializer<LocalDateTime> {

    private final DateTimeFormatter formatter;
    private final String pattern;

    public PlatformLocalDateTimeSerializer(DateTimeFormatter formatter) {
        this(formatter, null);
    }

    private PlatformLocalDateTimeSerializer(DateTimeFormatter formatter, String pattern) {
        this.formatter = formatter;
        this.pattern = pattern;
    }

    @Override
    public ValueSerializer<?> createContextual(SerializationContext ctxt, BeanProperty property) {
        if (property == null) {
            return this;
        }
        JsonFormat.Value format = property.findPropertyFormat(ctxt.getConfig(), handledType());
        if (format == null || !format.hasPattern() || format.getPattern().equals(pattern)) {
            return this;
        }
        String annotated = format.getPattern();
        return new PlatformLocalDateTimeSerializer(DateTimeFormatter.ofPattern(annotated), annotated);
    }

    @Override
    public Class<?> handledType() {
        return LocalDateTime.class;
    }

    @Override
    public void serialize(LocalDateTime value, JsonGenerator gen, SerializationContext context) {
        if (value == null) {
            gen.writeNull();
            return;
        }
        gen.writeString(formatter.format(value));
    }
}
