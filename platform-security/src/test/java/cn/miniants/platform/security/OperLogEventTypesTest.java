package cn.miniants.platform.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OperLogEventTypesTest {

    @Test
    void migrateLegacyGroup() {
        assertEquals(OperLogEventTypes.DATA_MUTATE, OperLogEventTypes.migrateLegacyGroup("write"));
        assertEquals(OperLogEventTypes.AUTH_UNCLASSIFIED, OperLogEventTypes.migrateLegacyGroup("unclassified"));
        assertEquals(OperLogEventTypes.AUTH_DENY, OperLogEventTypes.migrateLegacyGroup("deny"));
        assertNull(OperLogEventTypes.migrateLegacyGroup("ok"));
        assertEquals(OperLogEventTypes.LOGIN, OperLogEventTypes.migrateLegacyGroup("login"));
        assertEquals(OperLogEventTypes.RBAC_CHANGE, OperLogEventTypes.migrateLegacyGroup("rbac_change"));
        assertNull(OperLogEventTypes.migrateLegacyGroup("nope"));
    }

    @Test
    void knownIncludesPasswordChange() {
        assertTrue(OperLogEventTypes.isKnown(OperLogEventTypes.PASSWORD_CHANGE));
    }
}
