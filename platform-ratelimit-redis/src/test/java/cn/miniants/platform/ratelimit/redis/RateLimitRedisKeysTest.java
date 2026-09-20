package cn.miniants.platform.ratelimit.redis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitRedisKeysTest {

    @Test
    void counterUsesPrefixPolicyAndPlainSubject() {
        assertEquals(
                "platform:ratelimit:c:login:127.0.0.1",
                RateLimitRedisKeys.counter("platform:ratelimit:", "login", "127.0.0.1"));
    }

    @Test
    void parseCounterSplitsOnlyFirstColonAfterSegment() {
        RateLimitRedisKeys.CounterKey parsed = RateLimitRedisKeys.parseCounter(
                "jwy:yjs:ratelimit:",
                "jwy:yjs:ratelimit:c:auth.login:2001:db8::1");
        assertEquals("auth.login", parsed.policyCode());
        assertEquals("2001:db8::1", parsed.subject());
        assertEquals("jwy:yjs:ratelimit:c:*", RateLimitRedisKeys.counterScanPattern("jwy:yjs:ratelimit:"));
    }

    @Test
    void policySnapshotAndEventsReserved() {
        assertEquals("platform:ratelimit:policy:snapshot",
                RateLimitRedisKeys.policySnapshot("platform:ratelimit:"));
        assertEquals("platform:ratelimit:policy:events",
                RateLimitRedisKeys.policyEventsChannel("platform:ratelimit:"));
        assertEquals("platform:ratelimit:policy:revision",
                RateLimitRedisKeys.policyRevision("platform:ratelimit:"));
    }

    @Test
    void ttlIncludesGrace() {
        assertEquals(61_000L, RateLimitRedisKeys.ttlMillis(60_000L));
        assertThrows(IllegalArgumentException.class, () -> RateLimitRedisKeys.ttlMillis(0L));
    }

    @Test
    void subjectHashIsSha256HexTruncatedTo32() {
        String hash = RateLimitSubjectHasher.hash("user-1");
        assertEquals(32, hash.length());
        assertTrue(hash.matches("[0-9a-f]{32}"));
        assertEquals(hash, RateLimitSubjectHasher.hash("user-1"));
        assertNotEquals(hash, RateLimitSubjectHasher.hash("user-2"));
    }
}
