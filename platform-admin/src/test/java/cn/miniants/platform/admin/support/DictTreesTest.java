package cn.miniants.platform.admin.support;

import cn.miniants.platform.admin.dto.DictTreeNode;
import cn.miniants.platform.admin.entity.Dict;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DictTreesTest {

    @Test
    void buildsSortedTreeAndKeepsOrphansAsRoots() {
        Dict child = row(3L, 1L, 5, "child");
        Dict parent = row(1L, null, 10, "parent");
        Dict orphan = row(2L, 99L, 0, "orphan");

        List<DictTreeNode> tree = DictTrees.build(List.of(child, parent, orphan));

        assertEquals(List.of("orphan", "parent"),
                tree.stream().map(DictTreeNode::getDictCode).toList());
        assertEquals("child", tree.get(1).getChildren().get(0).getDictCode());
    }

    private static Dict row(Long id, Long parentId, Integer sortNo, String code) {
        Dict row = new Dict();
        row.setId(id);
        row.setParentId(parentId);
        row.setDictType("test");
        row.setDictCode(code);
        row.setDictLabel(code);
        row.setSortNo(sortNo);
        row.setStatus(1);
        return row;
    }
}
