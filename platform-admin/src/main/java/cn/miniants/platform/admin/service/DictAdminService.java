package cn.miniants.platform.admin.service;

import cn.miniants.platform.admin.dto.DictSave;
import cn.miniants.platform.admin.dto.DictTreeNode;
import cn.miniants.platform.admin.dto.DictVo;
import cn.miniants.platform.admin.dto.PageResult;

import java.util.List;

public interface DictAdminService {

    PageResult<DictVo> page(Long current, Long size, String filter, String order, Long parentId);

    DictVo get(Long id);

    List<DictTreeNode> tree(String dictType);

    DictVo check(DictSave body);

    DictVo save(DictSave body);

    void delete(Long id);
}
