package cn.miniants.platform.queue;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 队列任务时间字段（Asia/Shanghai ISO_LOCAL_DATE_TIME）。
 */
public final class QueueTimeFormats {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    public static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZONE);

    private QueueTimeFormats() {
    }

    public static String nowIso() {
        return ISO.format(Instant.now());
    }

    public static long toEpochMilli(LocalDateTime time) {
        return time.atZone(ZONE).toInstant().toEpochMilli();
    }

    public static boolean isOlderThan(String isoTime, long seconds, Instant now) {
        if (isoTime == null || isoTime.isBlank()) {
            return false;
        }
        try {
            Instant then = LocalDateTime.parse(isoTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .atZone(ZONE)
                    .toInstant();
            return then.plusSeconds(seconds).isBefore(now);
        } catch (DateTimeParseException ex) {
            try {
                Instant then = Instant.from(ISO.parse(isoTime));
                return then.plusSeconds(seconds).isBefore(now);
            } catch (DateTimeParseException ignored) {
                return false;
            }
        }
    }
}
