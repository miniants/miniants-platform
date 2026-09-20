package cn.miniants.platform.security.sas.keystore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtKidsTest {

    @Test
    void prefixWinsAndAppendsTimestamp() {
        String kid = JwtKids.next("jwt-yjs", "jwt");
        assertTrue(kid.startsWith("jwt-yjs-"));
        assertTrue(kid.matches("jwt-yjs-\\d{14}"));
    }

    @Test
    void blankPrefixFallsBackToAliasTimestamp() {
        String kid = JwtKids.next("  ", "jwt");
        assertTrue(kid.startsWith("jwt-"));
        assertTrue(kid.matches("jwt-\\d{14}"));
        assertFalse(kid.equals("jwt"));
    }

    @Test
    void blankPrefixAndAliasYieldsRandom() {
        String kid = JwtKids.next(null, "");
        assertFalse(kid.isBlank());
        assertFalse(kid.startsWith("-"));
    }
}
