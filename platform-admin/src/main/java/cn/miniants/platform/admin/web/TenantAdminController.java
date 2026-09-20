package cn.miniants.platform.admin.web;

import cn.miniants.platform.admin.dto.PageResult;
import cn.miniants.platform.admin.dto.TenantSave;
import cn.miniants.platform.admin.dto.TenantVo;
import cn.miniants.platform.admin.service.TenantAdminService;
import cn.miniants.platform.security.OperLog;
import cn.miniants.platform.security.OperLogEventTypes;
import cn.miniants.platform.security.Permission;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/platform/admin/tenant")
public class TenantAdminController {

    private final TenantAdminService tenantAdminService;

    public TenantAdminController(TenantAdminService tenantAdminService) {
        this.tenantAdminService = tenantAdminService;
    }

    @Permission("platform:tenant:page")
    @GetMapping("/page")
    public PageResult<TenantVo> page(
            @RequestParam(name = "current", required = false) Long current,
            @RequestParam(name = "size", required = false) Long size,
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(name = "order", required = false) String order) {
        return tenantAdminService.page(current, size, filter, order);
    }

    @Permission("platform:tenant:page")
    @GetMapping("/{id}")
    public TenantVo get(@PathVariable("id") Long id) {
        return tenantAdminService.get(id);
    }

    @Permission("platform:tenant:save")
    @OperLog(value = "保存租户", eventType = OperLogEventTypes.CONFIG_CHANGE)
    @PostMapping("/save")
    public TenantVo save(@RequestBody TenantSave body) {
        return tenantAdminService.save(body);
    }

    @Permission("platform:tenant:delete")
    @OperLog(value = "删除租户", eventType = OperLogEventTypes.CONFIG_CHANGE)
    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable("id") Long id) {
        tenantAdminService.delete(id);
        return Boolean.TRUE;
    }
}
