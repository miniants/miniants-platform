package cn.miniants.platform.admin.web;

import cn.miniants.platform.admin.dto.OperLogRecord;
import cn.miniants.platform.admin.dto.OperLogVo;
import cn.miniants.platform.admin.dto.PageResult;
import cn.miniants.platform.admin.service.OperLogAdminService;
import cn.miniants.platform.security.Permission;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/platform/admin/oper-log")
public class OperLogAdminController {

    private final OperLogAdminService operLogAdminService;

    public OperLogAdminController(OperLogAdminService operLogAdminService) {
        this.operLogAdminService = operLogAdminService;
    }

    @Permission("platform:oper-log:page")
    @GetMapping("/page")
    public PageResult<OperLogVo> page(
            @RequestParam(name = "current", required = false) Long current,
            @RequestParam(name = "size", required = false) Long size,
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(name = "order", required = false) String order,
            @RequestParam(name = "collapse", required = false) Boolean collapse) {
        return operLogAdminService.page(current, size, filter, order, Boolean.TRUE.equals(collapse));
    }

    @Permission("platform:oper-log:page")
    @GetMapping("/{id}")
    public OperLogVo get(@PathVariable("id") Long id) {
        return operLogAdminService.get(id);
    }

    @Permission("platform:oper-log:save")
    @PostMapping("/record")
    public OperLogVo record(@RequestBody OperLogRecord body) {
        return operLogAdminService.record(body);
    }
}
