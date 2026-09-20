package cn.miniants.platform.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeDrainLifecycleTest {

    @Test
    void stopFlipsDrain() {
        LocalRuntimeDrain drain = new LocalRuntimeDrain();
        RuntimeDrainLifecycle lifecycle = new RuntimeDrainLifecycle(drain);
        lifecycle.start();
        assertFalse(drain.isDraining());
        lifecycle.stop();
        assertTrue(drain.isDraining());
        assertFalse(lifecycle.isRunning());
    }
}
