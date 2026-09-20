package cn.miniants.platform.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RateLimitDurationsTest {

    @Test
    void parsesSimpleUnits() {
        assertEquals(Duration.ofSeconds(1), RateLimitDurations.parse("1s"));
        assertEquals(Duration.ofMinutes(1), RateLimitDurations.parse("1m"));
        assertEquals(Duration.ofHours(1), RateLimitDurations.parse("1h"));
        assertEquals(Duration.ofDays(1), RateLimitDurations.parse("1d"));
        assertEquals(Duration.ofMillis(500), RateLimitDurations.parse("500ms"));
    }

    @Test
    void parsesIso8601() {
        assertEquals(Duration.ofMinutes(1), RateLimitDurations.parse("PT1M"));
        assertEquals(Duration.ofDays(1), RateLimitDurations.parse("P1D"));
    }

    @Test
    void rejectsInvalid() {
        assertThrows(IllegalArgumentException.class, () -> RateLimitDurations.parse(""));
        assertThrows(IllegalArgumentException.class, () -> RateLimitDurations.parse("abc"));
        assertThrows(IllegalArgumentException.class, () -> RateLimitDurations.parse("0s"));
        assertThrows(IllegalArgumentException.class, () -> RateLimitDurations.parse("PT0S"));
    }
}
