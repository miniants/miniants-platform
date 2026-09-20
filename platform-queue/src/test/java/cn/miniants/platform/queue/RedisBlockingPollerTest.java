package cn.miniants.platform.queue;

import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RedisBlockingPollerTest {

    @Test
    void brpopShorterThanFourSecondClientTimeout() {
        assertEquals(3, RedisBlockingPoller.brpopSeconds(5, TimeUnit.SECONDS, 4000L));
    }

    @Test
    void brpopKeepsRequestedWhenClientTimeoutIsLonger() {
        assertEquals(5, RedisBlockingPoller.brpopSeconds(5, TimeUnit.SECONDS, 10_000L));
    }

    @Test
    void brpopAtLeastOneSecond() {
        assertEquals(1, RedisBlockingPoller.brpopSeconds(5, TimeUnit.SECONDS, 1500L));
    }

    @Test
    void idlePollTimeoutIsNotAFault() {
        QueryTimeoutException ex = new QueryTimeoutException(
                "Redis command timed out; nested exception is Command timed out after 4 second(s)");
        assertTrue(RedisDisconnectClassifier.isIdlePollTimeout(ex));
        assertFalse(RedisDisconnectClassifier.isTransientRedisDisconnect(ex));
    }
}
