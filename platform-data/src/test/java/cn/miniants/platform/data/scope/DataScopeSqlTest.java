package cn.miniants.platform.data.scope;

import cn.miniants.platform.core.error.PlatformException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DataScopeSqlTest {

    @Test
    void rewriteLeavesSqlWhenNoScope() {
        String sql = "SELECT * FROM building ORDER BY sort";
        assertEquals(sql, DataScopeSql.rewrite(sql, null));
        assertEquals(sql, DataScopeSql.rewrite(sql, DataScope.skip()));
    }

    @Test
    void rewriteWrapsNumericIds() {
        String sql = "SELECT * FROM building ORDER BY sort";
        DataScope scope = DataScope.in("id", List.of(1, 2L));
        assertEquals(
                "SELECT * FROM (SELECT * FROM building ORDER BY sort) _ds_t WHERE _ds_t.id IN (1,2)",
                DataScopeSql.rewrite(sql, scope));
    }

    @Test
    void emptyIdsSeeNothing() {
        String sql = "SELECT * FROM building";
        assertEquals(
                "SELECT * FROM (SELECT * FROM building) _ds_t WHERE 1=0",
                DataScopeSql.rewrite(sql, DataScope.none("id")));
    }

    @Test
    void rejectsUnsafeColumn() {
        assertThrows(PlatformException.class,
                () -> DataScopeSql.rewrite("SELECT 1", DataScope.in("id;drop", List.of(1L))));
    }

    @Test
    void findOnlyWhenParameterPresent() {
        assertNull(DataScope.find(null));
        assertNull(DataScope.find(Map.of("ew", "wrapper")));
        DataScope scope = DataScope.skip();
        assertSame(scope, DataScope.find(scope));
        assertSame(scope, DataScope.find(Map.of("ew", "wrapper", "dataScope", scope)));
    }
}
