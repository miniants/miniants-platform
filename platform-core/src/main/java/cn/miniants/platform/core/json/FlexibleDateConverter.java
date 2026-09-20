package cn.miniants.platform.core.json;

import org.springframework.core.convert.converter.Converter;
import org.jspecify.annotations.Nullable;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 表单 / query 把中文日期字符串转成 {@link Date}。空串当 null。
 */
public final class FlexibleDateConverter implements Converter<String, Date> {

    public static final FlexibleDateConverter INSTANCE = new FlexibleDateConverter();

    private FlexibleDateConverter() {
    }

    @Override
    @Nullable
    public Date convert(@Nullable String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        String value = source.trim();
        for (String pattern : PlatformDateTimeFormats.DATE_READ) {
            SimpleDateFormat format = new SimpleDateFormat(pattern);
            format.setLenient(false);
            ParsePosition pos = new ParsePosition(0);
            Date parsed = format.parse(value, pos);
            if (parsed != null && pos.getIndex() == value.length()) {
                return parsed;
            }
        }
        throw new IllegalArgumentException("期望 " + PlatformDateTimeFormats.DATE_TIME
                + " / " + PlatformDateTimeFormats.DATE_TIME_MINUTES
                + " / " + PlatformDateTimeFormats.DATE
                + " / " + PlatformDateTimeFormats.YEAR_MONTH);
    }
}
