package cn.miniants.platform.admin.dto;

import java.util.ArrayList;
import java.util.List;

public class ResourceTreeNode extends ResourceVo {

    private List<ResourceTreeNode> children = new ArrayList<>();

    public List<ResourceTreeNode> getChildren() {
        return children;
    }

    public void setChildren(List<ResourceTreeNode> children) {
        this.children = children == null ? new ArrayList<>() : children;
    }
}
