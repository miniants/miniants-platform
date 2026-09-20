package cn.miniants.platform.admin.web;

import cn.miniants.platform.admin.dto.PageResult;
import cn.miniants.platform.admin.dto.ResourceSave;
import cn.miniants.platform.admin.dto.ResourceTreeNode;
import cn.miniants.platform.admin.dto.ResourceVo;
import cn.miniants.platform.admin.service.ResourceAdminService;
import cn.miniants.platform.security.Authenticated;
import cn.miniants.platform.security.OperLog;
import cn.miniants.platform.security.OperLogEventTypes;
import cn.miniants.platform.security.Permission;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/platform/admin/resource")
public class ResourceAdminController {

    private final ResourceAdminService resourceAdminService;

    public ResourceAdminController(ResourceAdminService resourceAdminService) {
        this.resourceAdminService = resourceAdminService;
    }

    @Permission("platform:resource:page")
    @GetMapping("/page")
    public PageResult<ResourceVo> page(
            @RequestParam(name = "current", required = false) Long current,
            @RequestParam(name = "size", required = false) Long size,
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(name = "order", required = false) String order) {
        return resourceAdminService.page(current, size, filter, order);
    }

    @Permission("platform:resource:page")
    @GetMapping("/tree")
    public List<ResourceTreeNode> tree() {
        return resourceAdminService.tree();
    }

    @Authenticated
    @GetMapping("/mine")
    public List<ResourceTreeNode> mine() {
        return resourceAdminService.mine();
    }

    @Permission("platform:resource:page")
    @GetMapping("/{id}")
    public ResourceVo get(@PathVariable("id") Long id) {
        return resourceAdminService.get(id);
    }

    @Permission("platform:resource:save")
    @OperLog(value = "保存资源", eventType = OperLogEventTypes.RBAC_CHANGE)
    @PostMapping("/save")
    public ResourceVo save(@RequestBody ResourceSave body) {
        return resourceAdminService.save(body);
    }

    @Permission("platform:resource:delete")
    @OperLog(value = "删除资源", eventType = OperLogEventTypes.RBAC_CHANGE)
    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable("id") Long id) {
        resourceAdminService.delete(id);
        return Boolean.TRUE;
    }
}
