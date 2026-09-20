package cn.miniants.platform.queue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueueRoutingTest {

    @Test
    void singleUsesSameReadyKey() {
        QueueRouting routing = QueueRouting.single("jwy:sys:queue:demo:ready");
        assertEquals(List.of("jwy:sys:queue:demo:ready"), routing.pollKeys());
        assertEquals("jwy:sys:queue:demo:ready", routing.promoteReadyKey());
        assertEquals("jwy:sys:queue:demo:ready", routing.pushKey(Map.of("deviceNo", "x")));
    }

    @Test
    void singleRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> QueueRouting.single(""));
        assertThrows(IllegalArgumentException.class, () -> QueueRouting.single(null));
    }
}
