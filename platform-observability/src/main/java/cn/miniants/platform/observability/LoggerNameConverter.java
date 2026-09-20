package cn.miniants.platform.observability;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Logger 显示名：取显示名后；&lt;10 补到 10；&gt;10 按 20（不足补齐，过长保留右边 20）。
 */
public class LoggerNameConverter extends ClassicConverter {

    static final int SHORT_WIDTH = 10;
    static final int LONG_WIDTH = 20;

    @Override
    public String convert(ILoggingEvent event) {
        return format(event.getLoggerName());
    }

    public static String format(String loggerName) {
        String name = displayName(loggerName);
        int len = name.length();
        if (len < SHORT_WIDTH) {
            return String.format("%-" + SHORT_WIDTH + "s", name);
        }
        if (len <= SHORT_WIDTH) {
            return name;
        }
        if (len < LONG_WIDTH) {
            return String.format("%-" + LONG_WIDTH + "s", name);
        }
        return name.substring(len - LONG_WIDTH);
    }

    /**
     * Java 类 FQCN 取末段类名；显式短名（如 {@code pl.access}）整段保留。
     */
    static String displayName(String loggerName) {
        if (loggerName == null || loggerName.isEmpty()) {
            return "";
        }
        if (loggerName.startsWith("cn.binarywang") || loggerName.startsWith("me.chanjar.weixin")) {
            return "pl.wx";
        }
        int dot = loggerName.lastIndexOf('.');
        if (dot < 0 || dot >= loggerName.length() - 1) {
            return loggerName;
        }
        String last = loggerName.substring(dot + 1);
        if (!last.isEmpty() && Character.isUpperCase(last.charAt(0))) {
            return last;
        }
        return loggerName;
    }
}
