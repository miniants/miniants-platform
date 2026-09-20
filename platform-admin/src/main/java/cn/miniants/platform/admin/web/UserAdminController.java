package cn.miniants.platform.admin.web;

import cn.miniants.platform.admin.dto.PageResult;
import cn.miniants.platform.admin.dto.UserSave;
import cn.miniants.platform.admin.dto.UserVo;
import cn.miniants.platform.admin.service.UserAdminService;
import cn.miniants.platform.admin.support.PageQueries;
import cn.miniants.platform.security.OperLog;
import cn.miniants.platform.security.OperLogEventTypes;
import cn.miniants.platform.security.Permission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/platform/admin/user")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @Permission("platform:user:page")
    @GetMapping("/page")
    public PageResult<UserVo> page(
            @RequestParam(name = "current", required = false) Long current,
            @RequestParam(name = "size", required = false) Long size,
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(name = "order", required = false) String order,
            @RequestParam(name = "roleId", required = false) String roleId) {
        return userAdminService.page(current, size, filter, order, PageQueries.optionalLong(roleId));
    }

    @Permission("platform:user:page")
    @GetMapping("/{id}")
    public UserVo get(@PathVariable("id") Long id) {
        return userAdminService.get(id);
    }

    @Permission("platform:user:save")
    @OperLog(value = "保存用户", eventType = OperLogEventTypes.RBAC_CHANGE)
    @PostMapping("/save")
    public UserVo save(@Valid @RequestBody UserSave body) {
        return userAdminService.save(body);
    }

    @Permission("platform:user:delete")
    @OperLog(value = "删除用户", eventType = OperLogEventTypes.RBAC_CHANGE)
    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable("id") Long id) {
        userAdminService.delete(id);
        return Boolean.TRUE;
    }
}
