package cn.miniants.platform.core.json;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * HTTP JSON 与表单绑定共用的中文日期格式。对外 {@link java.time.LocalDateTime} 写
 * {@code yyyy-MM-dd HH:mm:ss}；读端兼容缺秒、ISO。审计字段（{@code createTime} /
 * {@code updateTime}）单独用到分，见实体上的 {@code @JsonFormat}。
 */
public final class PlatformDateTimeFormats {

    public static final String DATE_TIME = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_TIME_MINUTES = "yyyy-MM-dd HH:mm";
    public static final String DATE = "yyyy-MM-dd";
    public static final String YEAR_MONTH = "yyyy-MM";

    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME);
    public static final DateTimeFormatter DATE_TIME_MINUTES_FORMATTER =
            DateTimeFormatter.ofPattern(DATE_TIME_MINUTES);

    static final List<DateTimeFormatter> LOCAL_DATE_TIME_READ = List.of(
            DATE_TIME_FORMATTER,
            DATE_TIME_MINUTES_FORMATTER,
            DateTimeFormatter.ISO_LOCAL_DATE_TIME);

    static final List<String> DATE_READ = List.of(DATE_TIME, DATE_TIME_MINUTES, DATE, YEAR_MONTH);

    private PlatformDateTimeFormats() {
    }
}
