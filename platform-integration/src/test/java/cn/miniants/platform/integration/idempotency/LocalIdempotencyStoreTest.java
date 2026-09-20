package cn.miniants.platform.integration.idempotency;

import cn.miniants.platform.core.error.PlatformException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.*;

class LocalIdempotencyStoreTest {

    @Test
    void missingTtlIsRejected() {
        LocalIdempotencyStore store = new LocalIdempotencyStore();
        assertFalse(store.tryBegin("pay:1", null));
        assertFalse(store.tryBegin("pay:2", Duration.ZERO));
    }

    @Test
    void secondBeginIsRejected() {
        LocalIdempotencyStore store = new LocalIdempotencyStore();
        assertTrue(store.tryBegin("pay:1", Duration.ofMinutes(1)));
        assertFalse(store.tryBegin("pay:1", Duration.ofMinutes(1)));
        PlatformException ex = assertThrows(PlatformException.class,
                () -> store.requireBegin("pay:1", Duration.ofMinutes(1)));
        assertEquals("重复请求", ex.getMessage());
        assertTrue(store.tryBegin("pay:2", Duration.ofMinutes(1)));
    }
}
