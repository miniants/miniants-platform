package cn.miniants.platform.admin.support;

import cn.miniants.platform.admin.dto.ResourceTreeNode;
import cn.miniants.platform.admin.entity.Resource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceTreesTest {

    @Test
    void nestsByParentAndSorts() {
        List<ResourceTreeNode> tree = ResourceTrees.build(List.of(
                resource(2L, 1L, "platform:user:page", "用户", 1),
                resource(1L, null, "platform:system", "系统", 2),
                resource(3L, 1L, "platform:role:page", "角色", 0)));
        assertEquals(1, tree.size());
        assertEquals(1L, tree.get(0).getId());
        assertEquals("系统", tree.get(0).getName());
        assertEquals("系统", tree.get(0).getTitle());
        assertEquals("系统", tree.get(0).getLabel());
        assertEquals(List.of(3L, 2L), tree.get(0).getChildren().stream().map(ResourceTreeNode::getId).toList());
    }

    @Test
    void keepsIdZeroAsRoot() {
        List<ResourceTreeNode> tree = ResourceTrees.build(List.of(
                resource(0L, null, "platform:root", "根", 0),
                resource(8L, 0L, "platform:child", "子", 0)));
        assertEquals(0L, tree.get(0).getId());
        assertEquals(8L, tree.get(0).getChildren().get(0).getId());
    }

    @Test
    void visibleKeepsAncestorWhenChildGranted() {
        List<ResourceTreeNode> tree = ResourceTrees.build(List.of(
                resource(1L, null, "platform:system", "系统", 0),
                resource(2L, 1L, "platform:user:page", "用户", 0)));
        List<ResourceTreeNode> visible = ResourceTrees.visible(tree, Set.of("platform:user:page"), false);
        assertEquals(1, visible.size());
        assertEquals("platform:system", visible.get(0).getCode());
        assertEquals("platform:user:page", visible.get(0).getChildren().get(0).getCode());
    }

    @Test
    void visibleDropsUngrantedLeaf() {
        List<ResourceTreeNode> tree = ResourceTrees.build(List.of(
                resource(1L, null, "platform:system", "系统", 0),
                resource(2L, 1L, "platform:user:page", "用户", 0)));
        assertTrue(ResourceTrees.visible(tree, Set.of("other"), false).isEmpty());
    }

    private static Resource resource(Long id, Long parentId, String code, String name, int sortNo) {
        Resource row = new Resource();
        row.setId(id);
        row.setParentId(parentId);
        row.setCode(code);
        row.setName(name);
        row.setSortNo(sortNo);
        return row;
    }
}
