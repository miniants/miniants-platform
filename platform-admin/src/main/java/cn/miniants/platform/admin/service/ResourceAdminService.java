package cn.miniants.platform.admin.service;

import cn.miniants.platform.admin.dto.PageResult;
import cn.miniants.platform.admin.dto.ResourceSave;
import cn.miniants.platform.admin.dto.ResourceTreeNode;
import cn.miniants.platform.admin.dto.ResourceVo;

import java.util.List;

public interface ResourceAdminService {

    PageResult<ResourceVo> page(Long current, Long size, String filter, String order);

    List<ResourceTreeNode> tree();

    List<ResourceTreeNode> mine();

    ResourceVo get(Long id);

    ResourceVo save(ResourceSave body);

    void delete(Long id);
}
