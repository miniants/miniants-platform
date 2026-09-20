package cn.miniants.platform.admin.web;

import cn.miniants.platform.admin.dto.ConfigSave;
import cn.miniants.platform.admin.dto.ConfigVo;
import cn.miniants.platform.admin.dto.PageResult;
import cn.miniants.platform.admin.service.ConfigAdminService;
import cn.miniants.platform.security.OperLog;
import cn.miniants.platform.security.OperLogEventTypes;
import cn.miniants.platform.security.Permission;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/platform/admin/config")
public class ConfigAdminController {

    private final ConfigAdminService configAdminService;

    public ConfigAdminController(ConfigAdminService configAdminService) {
        this.configAdminService = configAdminService;
    }

    @Permission("platform:config:page")
    @GetMapping("/page")
    public PageResult<ConfigVo> page(
            @RequestParam(name = "current", required = false) Long current,
            @RequestParam(name = "size", required = false) Long size,
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(name = "order", required = false) String order) {
        return configAdminService.page(current, size, filter, order);
    }

    @Permission("platform:config:page")
    @GetMapping("/{id}")
    public ConfigVo get(@PathVariable("id") Long id) {
        return configAdminService.get(id);
    }

    @Permission("platform:config:save")
    @OperLog(value = "保存配置", eventType = OperLogEventTypes.CONFIG_CHANGE)
    @PostMapping("/save")
    public ConfigVo save(@RequestBody ConfigSave body) {
        return configAdminService.save(body);
    }

    @Permission("platform:config:delete")
    @OperLog(value = "删除配置", eventType = OperLogEventTypes.CONFIG_CHANGE)
    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable("id") Long id) {
        configAdminService.delete(id);
        return Boolean.TRUE;
    }
}
