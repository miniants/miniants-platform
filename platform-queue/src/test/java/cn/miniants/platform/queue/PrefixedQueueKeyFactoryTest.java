package cn.miniants.platform.queue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrefixedQueueKeyFactoryTest {

    @Test
    void buildsKeysWithPrefix() {
        QueueKeyFactory keys = new PrefixedQueueKeyFactory("jwy:sys:queue:");
        assertEquals("jwy:sys:queue:iot-cmd:ready", keys.ready("iot-cmd"));
        assertEquals("jwy:sys:queue:iot-cmd:ready:a1", keys.ready("iot-cmd", "a1"));
        assertEquals("jwy:sys:queue:iot-cmd:delay", keys.delay("iot-cmd"));
        assertEquals("jwy:sys:queue:iot-cmd:wake", keys.wake("iot-cmd"));
        assertEquals("jwy:sys:queue:iot-cmd:job:j1", keys.job("iot-cmd", "j1"));
        assertEquals("jwy:sys:queue:iot-cmd:lock:j1", keys.lock("iot-cmd", "j1"));
        assertEquals("jwy:sys:queue:iot-cmd:index", keys.index("iot-cmd"));
        assertEquals("jwy:sys:queue:iot-cmd:by:deviceNo:d1", keys.indexBy("iot-cmd", "deviceNo", "d1"));
    }

    @Test
    void rejectsBadPrefix() {
        assertThrows(IllegalArgumentException.class, () -> new PrefixedQueueKeyFactory("jwy:sys:queue"));
        assertThrows(IllegalArgumentException.class, () -> new PrefixedQueueKeyFactory(""));
    }
}
