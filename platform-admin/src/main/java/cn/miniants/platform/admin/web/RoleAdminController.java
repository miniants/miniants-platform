package cn.miniants.platform.admin.web;

import cn.miniants.platform.admin.dto.PageResult;
import cn.miniants.platform.admin.dto.RoleSave;
import cn.miniants.platform.admin.dto.RoleVo;
import cn.miniants.platform.admin.service.RoleAdminService;
import cn.miniants.platform.security.OperLog;
import cn.miniants.platform.security.OperLogEventTypes;
import cn.miniants.platform.security.Permission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/platform/admin/role")
public class RoleAdminController {

    private final RoleAdminService roleAdminService;

    public RoleAdminController(RoleAdminService roleAdminService) {
        this.roleAdminService = roleAdminService;
    }

    @Permission("platform:role:page")
    @GetMapping("/page")
    public PageResult<RoleVo> page(
            @RequestParam(name = "current", required = false) Long current,
            @RequestParam(name = "size", required = false) Long size,
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(name = "order", required = false) String order) {
        return roleAdminService.page(current, size, filter, order);
    }

    @Permission("platform:role:page")
    @GetMapping("/{id}")
    public RoleVo get(@PathVariable("id") Long id) {
        return roleAdminService.get(id);
    }

    @Permission("platform:role:save")
    @OperLog(value = "保存角色", eventType = OperLogEventTypes.RBAC_CHANGE)
    @PostMapping("/save")
    public RoleVo save(@Valid @RequestBody RoleSave body) {
        return roleAdminService.save(body);
    }

    @Permission("platform:role:delete")
    @OperLog(value = "删除角色", eventType = OperLogEventTypes.RBAC_CHANGE)
    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable("id") Long id) {
        roleAdminService.delete(id);
        return Boolean.TRUE;
    }
}
