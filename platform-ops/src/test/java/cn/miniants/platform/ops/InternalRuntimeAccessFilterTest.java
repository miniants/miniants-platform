package cn.miniants.platform.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalRuntimeAccessFilterTest {

    @Test
    void acceptsOnlyLoopback() {
        assertTrue(InternalRuntimeAccessFilter.isLoopback("127.0.0.1"));
        assertTrue(InternalRuntimeAccessFilter.isLoopback("::1"));
        assertFalse(InternalRuntimeAccessFilter.isLoopback("10.0.0.8"));
        assertFalse(InternalRuntimeAccessFilter.isLoopback(null));
        assertFalse(InternalRuntimeAccessFilter.isLoopback(""));
    }
}
