package cn.miniants.platform.ratelimit.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RateLimitSubjectKeyTest {

    @Test
    void sanitizeKeepsPlainIpAndUsername() {
        assertEquals("127.0.0.1", RateLimitSubjectKey.sanitize("127.0.0.1"));
        assertEquals("admin", RateLimitSubjectKey.sanitize("admin"));
        assertEquals("2001:db8::1", RateLimitSubjectKey.sanitize("2001:db8::1"));
    }

    @Test
    void sanitizeStripsControlsAndCapsLength() {
        assertEquals("ab", RateLimitSubjectKey.sanitize("a\nb"));
        assertEquals(RateLimitSubjectKey.UNKNOWN, RateLimitSubjectKey.sanitize("   "));
        String longSubject = "x".repeat(200);
        assertEquals(128, RateLimitSubjectKey.sanitize(longSubject).length());
        assertFalse(RateLimitSubjectKey.sanitize("user\0id").contains("\0"));
    }
}
