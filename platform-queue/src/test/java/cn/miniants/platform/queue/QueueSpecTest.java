package cn.miniants.platform.queue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueueSpecTest {

    @Test
    void acceptsKebabName() {
        QueueSpec.requireName("iot-cmd");
        QueueSpec.requireName("wbcu-due");
    }

    @Test
    void rejectsBlankAndUppercase() {
        assertThrows(IllegalArgumentException.class, () -> QueueSpec.requireName(null));
        assertThrows(IllegalArgumentException.class, () -> QueueSpec.requireName(""));
        assertThrows(IllegalArgumentException.class, () -> QueueSpec.requireName("IoT"));
        assertThrows(IllegalArgumentException.class, () -> QueueSpec.requireName("1abc"));
    }

    @Test
    void namePatternMessage() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> QueueSpec.requireName("BAD"));
        assertEquals("队列名须为小写字母开头的短横线名: BAD", ex.getMessage());
    }
}
