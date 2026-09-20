package cn.miniants.platform.admin.support;

import cn.miniants.platform.admin.dto.DictTreeNode;
import cn.miniants.platform.admin.entity.Dict;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DictTrees {

    private DictTrees() {
    }

    public static List<DictTreeNode> build(List<Dict> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<Long, DictTreeNode> nodes = new LinkedHashMap<>();
        for (Dict row : rows) {
            if (row != null && row.getId() != null) {
                nodes.put(row.getId(), toNode(row));
            }
        }
        List<DictTreeNode> roots = new ArrayList<>();
        for (Dict row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            DictTreeNode node = nodes.get(row.getId());
            DictTreeNode parent = row.getParentId() == null ? null : nodes.get(row.getParentId());
            if (parent == null || parent == node) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        sort(roots);
        return roots;
    }

    private static void sort(List<DictTreeNode> nodes) {
        nodes.sort(Comparator
                .comparing((DictTreeNode node) -> node.getSortNo() == null ? 0 : node.getSortNo())
                .thenComparing(node -> node.getId() == null ? 0L : node.getId()));
        for (DictTreeNode node : nodes) {
            sort(node.getChildren());
        }
    }

    private static DictTreeNode toNode(Dict row) {
        DictTreeNode node = new DictTreeNode();
        node.setId(row.getId());
        node.setParentId(row.getParentId());
        node.setDictType(row.getDictType());
        node.setDictCode(row.getDictCode());
        node.setDictLabel(row.getDictLabel());
        node.setContent(row.getContent());
        node.setRemark(row.getRemark());
        node.setSortNo(row.getSortNo());
        node.setStatus(row.getStatus());
        node.setVersion(row.getVersion());
        node.setCreateTime(row.getCreateTime());
        node.setUpdateTime(row.getUpdateTime());
        return node;
    }
}
