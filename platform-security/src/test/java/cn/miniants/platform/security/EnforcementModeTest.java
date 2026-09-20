package cn.miniants.platform.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnforcementModeTest {

    @Test
    void blankDefaultsToShadow() {
        assertEquals(EnforcementMode.SHADOW, EnforcementMode.from(null));
        assertEquals(EnforcementMode.SHADOW, EnforcementMode.from(" "));
        assertEquals(EnforcementMode.SHADOW, EnforcementMode.from("weird"));
        assertEquals(EnforcementMode.OFF, EnforcementMode.from("off"));
        assertEquals(EnforcementMode.ENFORCE, EnforcementMode.from("ENFORCE"));
    }
}
