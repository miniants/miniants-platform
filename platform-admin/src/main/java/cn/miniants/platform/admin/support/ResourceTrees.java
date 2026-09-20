package cn.miniants.platform.admin.support;

import cn.miniants.platform.admin.dto.ResourceTreeNode;
import cn.miniants.platform.admin.entity.Resource;
import cn.miniants.platform.admin.entity.ResourceMeta;

import java.util.*;

public final class ResourceTrees {

    private ResourceTrees() {
    }

    public static List<ResourceTreeNode> build(List<Resource> rows) {
        return build(rows, Map.of());
    }

    public static List<ResourceTreeNode> build(List<Resource> rows, Map<Long, ResourceMeta> metaByResourceId) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<Long, ResourceMeta> metas = metaByResourceId == null ? Map.of() : metaByResourceId;
        Map<Long, ResourceTreeNode> nodes = new LinkedHashMap<>();
        for (Resource row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            ResourceTreeNode node = toNode(row);
            node.setMeta(ResourceMetas.toDto(metas.get(row.getId())));
            nodes.put(row.getId(), node);
        }
        List<ResourceTreeNode> roots = new ArrayList<>();
        for (Resource row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            ResourceTreeNode node = nodes.get(row.getId());
            Long parentId = row.getParentId();
            ResourceTreeNode parent = parentId == null ? null : nodes.get(parentId);
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        sort(roots);
        return roots;
    }

    public static List<ResourceTreeNode> visible(List<ResourceTreeNode> tree, Set<String> codes, boolean sysAdmin) {
        if (tree == null || tree.isEmpty()) {
            return List.of();
        }
        if (sysAdmin) {
            return tree;
        }
        Set<String> granted = codes == null ? Set.of() : codes;
        List<ResourceTreeNode> kept = new ArrayList<>();
        for (ResourceTreeNode node : tree) {
            ResourceTreeNode copy = keep(node, granted);
            if (copy != null) {
                kept.add(copy);
            }
        }
        return kept;
    }

    private static ResourceTreeNode keep(ResourceTreeNode node, Set<String> granted) {
        List<ResourceTreeNode> children = new ArrayList<>();
        for (ResourceTreeNode child : node.getChildren()) {
            ResourceTreeNode kept = keep(child, granted);
            if (kept != null) {
                children.add(kept);
            }
        }
        boolean self = node.getCode() != null && granted.contains(node.getCode());
        if (!self && children.isEmpty()) {
            return null;
        }
        ResourceTreeNode copy = copyWithoutChildren(node);
        copy.setChildren(children);
        return copy;
    }

    private static void sort(List<ResourceTreeNode> nodes) {
        nodes.sort(Comparator
                .comparing((ResourceTreeNode n) -> n.getSortNo() == null ? 0 : n.getSortNo())
                .thenComparing(n -> n.getId() == null ? 0L : n.getId()));
        for (ResourceTreeNode node : nodes) {
            sort(node.getChildren());
        }
    }

    private static ResourceTreeNode toNode(Resource row) {
        ResourceTreeNode node = new ResourceTreeNode();
        node.setId(row.getId());
        node.setParentId(row.getParentId());
        node.setCode(row.getCode());
        node.setName(row.getName());
        node.setType(row.getType());
        node.setPath(row.getPath());
        node.setSortNo(row.getSortNo());
        node.setStatus(row.getStatus());
        return node;
    }

    private static ResourceTreeNode copyWithoutChildren(ResourceTreeNode node) {
        ResourceTreeNode copy = new ResourceTreeNode();
        copy.setId(node.getId());
        copy.setParentId(node.getParentId());
        copy.setCode(node.getCode());
        copy.setName(node.getName());
        copy.setType(node.getType());
        copy.setPath(node.getPath());
        copy.setSortNo(node.getSortNo());
        copy.setStatus(node.getStatus());
        copy.setVersion(node.getVersion());
        copy.setCreateTime(node.getCreateTime());
        copy.setUpdateTime(node.getUpdateTime());
        copy.setMeta(node.getMeta());
        return copy;
    }
}
