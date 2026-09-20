package cn.miniants.platform.queue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetryBackoffTest {

    @Test
    void stepsThenCaps() {
        assertEquals(30L, RetryBackoff.secondsAfterAttempt(1));
        assertEquals(120L, RetryBackoff.secondsAfterAttempt(2));
        assertEquals(300L, RetryBackoff.secondsAfterAttempt(3));
        assertEquals(300L, RetryBackoff.secondsAfterAttempt(10));
    }
}
