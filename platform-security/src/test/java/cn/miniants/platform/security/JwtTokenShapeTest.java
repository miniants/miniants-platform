package cn.miniants.platform.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenShapeTest {

    @Test
    void looksLikeJwtRequiresThreeSegments() {
        assertTrue(JwtTokenShape.looksLikeJwt("a.b.c"));
        assertFalse(JwtTokenShape.looksLikeJwt("a.b"));
        assertFalse(JwtTokenShape.looksLikeJwt(null));
    }

    @Test
    void describeDoesNotEchoToken() {
        assertEquals("jwt-like dots=2 len=5", JwtTokenShape.describe("a.b.c"));
        assertEquals("null", JwtTokenShape.describe(null));
        assertEquals("empty", JwtTokenShape.describe("  "));
    }
}
