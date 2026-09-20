package cn.miniants.platform.tenant;

import cn.miniants.platform.core.error.PlatformException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenantContextTest {

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void bindAndRequire() {
        TenantContext.bind("alpha");
        assertEquals("alpha", TenantContext.require());
        assertTrue(TenantContext.current().isPresent());
    }

    @Test
    void blankClears() {
        TenantContext.bind("alpha");
        TenantContext.bind("  ");
        assertTrue(TenantContext.current().isEmpty());
        assertThrows(PlatformException.class, TenantContext::require);
    }
}
