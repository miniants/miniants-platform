package cn.miniants.platform.data.audit;

import cn.miniants.platform.data.entity.LogicDeleteEntity;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PlatformMetaObjectHandlerTest {

    @Test
    void insertFillsAuditAndVersionFromAuditor() {
        LogicDeleteEntity entity = new LogicDeleteEntity();
        PlatformMetaObjectHandler handler = new PlatformMetaObjectHandler(
                () -> Optional.of(new Auditor(9L, "alice")));
        handler.insertFill(SystemMetaObject.forObject(entity));
        assertEquals(9L, entity.getCreateId());
        assertEquals("alice", entity.getCreateBy());
        assertNotNull(entity.getCreateTime());
        assertEquals(1L, entity.getVersion());
        assertNull(entity.getUpdateTime());
    }

    @Test
    void insertFallsBackToSystemWhenNoAuditor() {
        LogicDeleteEntity entity = new LogicDeleteEntity();
        PlatformMetaObjectHandler handler = new PlatformMetaObjectHandler(Optional::empty);
        handler.insertFill(SystemMetaObject.forObject(entity));
        assertEquals(-1L, entity.getCreateId());
        assertEquals("system", entity.getCreateBy());
    }

    @Test
    void updateFillsModifierOnly() {
        LogicDeleteEntity entity = new LogicDeleteEntity();
        PlatformMetaObjectHandler handler = new PlatformMetaObjectHandler(
                () -> Optional.of(new Auditor(2L, "bob")));
        handler.updateFill(SystemMetaObject.forObject(entity));
        assertEquals("bob", entity.getUpdateBy());
        assertNotNull(entity.getUpdateTime());
        assertNull(entity.getCreateBy());
    }
}
