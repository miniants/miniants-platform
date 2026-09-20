package cn.miniants.platform.admin.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistIdsTest {

    @Test
    void zeroIsPersisted() {
        assertTrue(PersistIds.persisted(0L));
        assertTrue(PersistIds.persisted(1L));
        assertFalse(PersistIds.persisted(null));
    }
}
