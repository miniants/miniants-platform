package cn.miniants.platform.queue;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueueTimeFormatsTest {

    @Test
    void olderThanUsesShanghaiLocal() {
        Instant now = Instant.parse("2026-08-19T00:10:00Z");
        String iso = "2026-08-19T08:00:00";
        assertTrue(QueueTimeFormats.isOlderThan(iso, 60, now));
        assertFalse(QueueTimeFormats.isOlderThan(iso, 3600, now));
        assertFalse(QueueTimeFormats.isOlderThan("", 1, now));
        assertFalse(QueueTimeFormats.isOlderThan(null, 1, now));
    }

    @Test
    void epochMilliMatchesZone() {
        long ms = QueueTimeFormats.toEpochMilli(LocalDateTime.of(2026, 8, 19, 8, 0, 0));
        assertTrue(ms > 0);
    }
}
