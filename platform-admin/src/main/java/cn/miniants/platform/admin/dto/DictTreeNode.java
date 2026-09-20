package cn.miniants.platform.admin.dto;

import java.util.ArrayList;
import java.util.List;

public class DictTreeNode extends DictVo {

    private List<DictTreeNode> children = new ArrayList<>();

    public List<DictTreeNode> getChildren() {
        return children;
    }

    public void setChildren(List<DictTreeNode> children) {
        this.children = children == null ? new ArrayList<>() : children;
    }
}
