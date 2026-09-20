package cn.miniants.platform.data.query;

import cn.miniants.platform.data.support.GoldenSnapshot;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityQueryTest {

    private static final String FILTER = "{\"realName\":{\"$like\":\"张\"},\"academyId\":3,"
            + "\"status\":{\"$in\":[1,2]},\"score\":{\"$ge\":60},"
            + "\"$or\":{\"deleted\":{\"$eq\":0},\"remark\":{\"$isNull\":\"1\"}}}";
    private static final String ORDER = "{\"createTime\":\"DESC\",\"id\":\"ASC\"}";

    @Test
    void filterAndOrderSqlSegmentMatchesSnapshot() {
        QueryWrapper<?> wrapper = EntityQuery.of(QuerySample.class).wrapper(FILTER, ORDER);
        List<String> lines = new ArrayList<>();
        lines.add("sql=" + wrapper.getSqlSegment());
        lines.add("target=" + wrapper.getTargetSql());
        lines.add("params=" + wrapper.getParamNameValuePairs());
        GoldenSnapshot.assertResource(getClass(), "/golden/entity-query.txt", lines);
        String sql = wrapper.getSqlSegment();
        assertTrue(sql.contains("real_name"), sql);
        assertTrue(sql.contains("academy_id"), sql);
        assertTrue(sql.contains("create_time"), sql);
    }

    @Test
    void rejectsUnknownOperator() {
        assertThrows(IllegalArgumentException.class,
                () -> EntityQuery.of(QuerySample.class).wrapper("{\"realName\":{\"$unknown\":\"x\"}}", null));
    }

    @Test
    void rejectsUnknownOrder() {
        assertThrows(IllegalArgumentException.class,
                () -> EntityQuery.of(QuerySample.class).wrapper(null, "{\"createTime\":\"DOWN\"}"));
    }

    @Test
    void rejectsUnknownField() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> EntityQuery.of(QuerySample.class).wrapper("{\"secret\":\"1\"}", null));
        assertTrue(ex.getMessage().contains("secret"), ex.getMessage());
    }

    @Test
    void rejectsHiddenPassword() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> EntityQuery.of(QuerySample.class).wrapper("{\"password\":\"x\"}", null));
        assertTrue(ex.getMessage().contains("password"), ex.getMessage());
    }

    @Test
    void rejectsExistFalseField() {
        assertThrows(IllegalArgumentException.class,
                () -> EntityQuery.of(QuerySample.class).wrapper("{\"extra\":\"x\"}", null));
    }

    @Test
    void rejectsExplicitExclude() {
        assertThrows(IllegalArgumentException.class,
                () -> EntityQuery.of(QuerySample.class).exclude("academyId")
                        .wrapper("{\"academyId\":3}", null));
    }

    @Test
    void rejectsUnknownOrderField() {
        assertThrows(IllegalArgumentException.class,
                () -> EntityQuery.of(QuerySample.class).wrapper(null, "{\"secret\":\"ASC\"}"));
    }

    @Test
    void filterAcceptsUriEncodedJson() {
        QueryWrapper<?> wrapper = EntityQuery.of(QuerySample.class)
                .wrapper("%7B%22realName%22%3A%7B%22%24like%22%3A%22a%22%7D%7D", null);
        String sql = wrapper.getSqlSegment();
        assertTrue(sql.contains("real_name"), sql);
    }

    @Test
    void emptyIdAndEmptyAndAreSkipped() {
        QueryWrapper<?> wrapper = EntityQuery.of(QuerySample.class)
                .wrapper("{\"id\":\"\",\"$and\":{}}", null);
        String sql = wrapper.getSqlSegment();
        assertTrue(sql == null || sql.isBlank() || !sql.contains("id"), String.valueOf(sql));
    }

    @Test
    void tableAliasPrefixesColumnsAndAcceptsQualifiedKeys() {
        QueryWrapper<?> wrapper = EntityQuery.of(QuerySample.class).tableAlias("r")
                .wrapper("{\"academyId\":3,\"r.realName\":{\"$like\":\"张\"}}", "{\"r.createTime\":\"DESC\"}");
        String sql = wrapper.getSqlSegment();
        assertTrue(sql.contains("r.academy_id"), sql);
        assertTrue(sql.contains("r.real_name"), sql);
        assertTrue(sql.contains("r.create_time"), sql);
    }

    @Test
    void tableAliasRejectsUnregisteredPrefix() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> EntityQuery.of(QuerySample.class).tableAlias("r")
                        .wrapper("{\"sa.id\":1}", null));
        assertTrue(ex.getMessage().contains("sa.id"), ex.getMessage());
    }

    @Test
    void allowRegistersSecondEntityPrefix() {
        QueryWrapper<?> wrapper = EntityQuery.of(QuerySample.class).tableAlias("stu")
                .allow("sa", QuerySample.class)
                .wrapper("{\"stu.realName\":{\"$like\":\"张\"},\"sa.id\":1,\"academyId\":3}",
                        "{\"stu.createTime\":\"DESC\"}");
        String sql = wrapper.getSqlSegment();
        assertTrue(sql.contains("stu.real_name"), sql);
        assertTrue(sql.contains("sa.id"), sql);
        assertTrue(sql.contains("stu.academy_id"), sql);
        assertTrue(sql.contains("stu.create_time"), sql);
    }

    @Test
    void allowSameEntityTwoAliases() {
        QueryWrapper<?> wrapper = EntityQuery.of(QuerySample.class).tableAlias("room")
                .allow("ivg1", QuerySample.class)
                .allow("ivg2", QuerySample.class)
                .wrapper("{\"ivg1.realName\":{\"$like\":\"张\"},\"$or\":{\"ivg2.realName\":{\"$like\":\"张\"}}}", null);
        String sql = wrapper.getSqlSegment();
        assertTrue(sql.contains("ivg1.real_name"), sql);
        assertTrue(sql.contains("ivg2.real_name"), sql);
    }

    @Test
    void allowStillRejectsUnregisteredPrefix() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> EntityQuery.of(QuerySample.class).tableAlias("stu")
                        .allow("sa", QuerySample.class)
                        .wrapper("{\"zzz.not_a_column\":\"1\"}", null));
        assertTrue(ex.getMessage().contains("zzz.not_a_column"), ex.getMessage());
    }

    @Test
    void allowRejectsDuplicateAlias() {
        assertThrows(IllegalArgumentException.class,
                () -> EntityQuery.of(QuerySample.class).tableAlias("stu").allow("stu", QuerySample.class));
    }
}
