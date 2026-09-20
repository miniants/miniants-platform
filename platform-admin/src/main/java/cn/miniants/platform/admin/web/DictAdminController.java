package cn.miniants.platform.admin.web;

import cn.miniants.platform.admin.dto.DictSave;
import cn.miniants.platform.admin.dto.DictTreeNode;
import cn.miniants.platform.admin.dto.DictVo;
import cn.miniants.platform.admin.dto.PageResult;
import cn.miniants.platform.admin.service.DictAdminService;
import cn.miniants.platform.admin.support.PageQueries;
import cn.miniants.platform.security.OperLog;
import cn.miniants.platform.security.OperLogEventTypes;
import cn.miniants.platform.security.Permission;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/platform/admin/dict")
public class DictAdminController {

    private final DictAdminService dictAdminService;

    public DictAdminController(DictAdminService dictAdminService) {
        this.dictAdminService = dictAdminService;
    }

    @Permission("platform:dict:page")
    @GetMapping("/page")
    public PageResult<DictVo> page(
            @RequestParam(name = "current", required = false) Long current,
            @RequestParam(name = "size", required = false) Long size,
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(name = "order", required = false) String order,
            @RequestParam(name = "parentId", required = false) String parentId) {
        return dictAdminService.page(current, size, filter, order, PageQueries.optionalLong(parentId));
    }

    @Permission("platform:dict:page")
    @GetMapping("/{id}")
    public DictVo get(@PathVariable("id") Long id) {
        return dictAdminService.get(id);
    }

    @Permission("platform:dict:page")
    @GetMapping("/tree")
    public List<DictTreeNode> tree(
            @RequestParam(name = "dictType", required = false) String dictType) {
        return dictAdminService.tree(dictType);
    }

    @Permission("platform:dict:save")
    @PostMapping("/check")
    public DictVo check(@RequestBody DictSave body) {
        return dictAdminService.check(body);
    }

    @Permission("platform:dict:save")
    @OperLog(value = "保存字典", eventType = OperLogEventTypes.DATA_MUTATE)
    @PostMapping("/save")
    public DictVo save(@RequestBody DictSave body) {
        return dictAdminService.save(body);
    }

    @Permission("platform:dict:delete")
    @OperLog(value = "删除字典", eventType = OperLogEventTypes.DATA_MUTATE)
    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable("id") Long id) {
        dictAdminService.delete(id);
        return Boolean.TRUE;
    }
}
