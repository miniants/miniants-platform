package cn.miniants.platform.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceIdsTest {

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void newTraceIdIs16Hex() {
        assertTrue(TraceIds.newTraceId().matches("[0-9a-f]{16}"));
    }

    @Test
    void keepsSafeIncomingAndTruncates() {
        assertEquals("a1b2c3d4e5f60708", TraceIds.resolve("a1b2c3d4e5f60708"));
        assertEquals("0123456789abcdef", TraceIds.resolve("0123456789abcdefEXTRA"));
    }

    @Test
    void rejectsUnsafeIncoming() {
        assertTrue(TraceIds.resolve("bad id with spaces").matches("[0-9a-f]{16}"));
        assertTrue(TraceIds.resolve("").matches("[0-9a-f]{16}"));
    }

    @Test
    void bindAndCurrent() {
        TraceIds.bind("a1b2c3d4e5f60708");
        assertEquals("a1b2c3d4e5f60708", TraceIds.current().orElseThrow());
        TraceIds.clear();
        assertTrue(TraceIds.current().isEmpty());
    }

    @Test
    void bindIpAndClear() {
        TraceIds.bindIp("10.0.0.8");
        assertEquals("10.0.0.8", MDC.get(TraceIds.MDC_IP));
        TraceIds.bindIp(" ");
        assertEquals(TraceIds.ANON_IP, MDC.get(TraceIds.MDC_IP));
        TraceIds.clear();
        assertNull(MDC.get(TraceIds.MDC_IP));
    }
}
