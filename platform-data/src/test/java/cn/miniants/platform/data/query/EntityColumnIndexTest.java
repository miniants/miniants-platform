package cn.miniants.platform.data.query;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EntityColumnIndexTest {

    @Test
    void mapsCamelAndUnderlineToSameColumn() {
        EntityColumnIndex index = new EntityColumnIndex(QuerySample.class, Set.of());
        assertEquals("real_name", index.requireColumn("realName", "查询"));
        assertEquals("real_name", index.requireColumn("real_name", "查询"));
        assertTrue(index.columns().contains("create_time"));
    }

    @Test
    void hiddenAndMissingAreRejected() {
        EntityColumnIndex index = new EntityColumnIndex(QuerySample.class, Set.of("remark"));
        assertThrows(IllegalArgumentException.class, () -> index.requireColumn("password", "查询"));
        assertThrows(IllegalArgumentException.class, () -> index.requireColumn("extra", "查询"));
        assertThrows(IllegalArgumentException.class, () -> index.requireColumn("remark", "查询"));
        assertThrows(IllegalArgumentException.class, () -> index.requireColumn("unknown", "查询"));
    }
}
