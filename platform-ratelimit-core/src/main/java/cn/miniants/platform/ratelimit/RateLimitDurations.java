package cn.miniants.platform.ratelimit;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析简写周期（{@code 1s}/{@code 1m}/{@code 1h}/{@code 1d}）与 ISO-8601 时长。
 */
public final class RateLimitDurations {

    private static final Pattern SIMPLE = Pattern.compile("^(\\d+)(ms|s|m|h|d)$", Pattern.CASE_INSENSITIVE);

    private RateLimitDurations() {
    }

    /**
     * @param text 如 {@code 1s}、{@code 5m}、{@code 1h}、{@code 1d}、{@code 500ms}、{@code PT1M}、{@code P1D}
     */
    public static Duration parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("时长不能为空");
        }
        String trimmed = text.trim();
        Matcher matcher = SIMPLE.matcher(trimmed);
        if (matcher.matches()) {
            long amount = Long.parseLong(matcher.group(1));
            if (amount <= 0) {
                throw new IllegalArgumentException("时长必须为正数: " + text);
            }
            String unit = matcher.group(2).toLowerCase(Locale.ROOT);
            return switch (unit) {
                case "ms" -> Duration.ofMillis(amount);
                case "s" -> Duration.ofSeconds(amount);
                case "m" -> Duration.ofMinutes(amount);
                case "h" -> Duration.ofHours(amount);
                case "d" -> Duration.ofDays(amount);
                default -> throw new IllegalArgumentException("不支持的时长单位: " + text);
            };
        }
        try {
            Duration iso = Duration.parse(trimmed);
            if (iso.isZero() || iso.isNegative()) {
                throw new IllegalArgumentException("时长必须为正: " + text);
            }
            return iso;
        } catch (Exception ex) {
            throw new IllegalArgumentException("无法解析时长: " + text, ex);
        }
    }
}
