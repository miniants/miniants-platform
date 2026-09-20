package cn.miniants.platform.starter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MvcStarterIsolationTest {

    @Test
    void webfluxRateLimitIsNotOnClasspath() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("cn.miniants.platform.ratelimit.webflux.RateLimitWebFilter"));
    }
}
