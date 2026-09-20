package cn.miniants.platform.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MdcTaskDecoratorTest {

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void copiesTraceIdToWorkerAndRestoresCaller() throws Exception {
        MDC.put(TraceIds.MDC_TRACE_ID, "a1b2c3d4e5f60708");
        AtomicReference<String> worker = new AtomicReference<>();
        Runnable decorated = new MdcTaskDecorator().decorate(() -> worker.set(MDC.get(TraceIds.MDC_TRACE_ID)));

        Thread workerThread = new Thread(() -> {
            decorated.run();
            assertNull(MDC.get(TraceIds.MDC_TRACE_ID));
        });
        workerThread.start();
        workerThread.join();

        assertEquals("a1b2c3d4e5f60708", worker.get());
        assertEquals("a1b2c3d4e5f60708", MDC.get(TraceIds.MDC_TRACE_ID));
    }
}
