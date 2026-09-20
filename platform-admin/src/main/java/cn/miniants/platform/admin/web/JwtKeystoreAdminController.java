package cn.miniants.platform.admin.web;

import cn.miniants.platform.admin.dto.JwtKeystoreVo;
import cn.miniants.platform.admin.dto.JwtWrapSettingsVo;
import cn.miniants.platform.admin.dto.JwtWrapSourceSave;
import cn.miniants.platform.admin.dto.PageResult;
import cn.miniants.platform.admin.service.JwtKeystoreAdminService;
import cn.miniants.platform.security.OperLog;
import cn.miniants.platform.security.OperLogEventTypes;
import cn.miniants.platform.security.Permission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/platform/admin/jwt-keystore")
public class JwtKeystoreAdminController {

    private final JwtKeystoreAdminService jwtKeystoreAdminService;

    public JwtKeystoreAdminController(JwtKeystoreAdminService jwtKeystoreAdminService) {
        this.jwtKeystoreAdminService = jwtKeystoreAdminService;
    }

    @Permission("platform:jwt-keystore:page")
    @GetMapping("/page")
    public PageResult<JwtKeystoreVo> page(
            @RequestParam(name = "current", required = false) Long current,
            @RequestParam(name = "size", required = false) Long size,
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(name = "order", required = false) String order) {
        return jwtKeystoreAdminService.page(current, size, filter, order);
    }

    @Permission("platform:jwt-keystore:page")
    @GetMapping("/list")
    public List<JwtKeystoreVo> list() {
        return jwtKeystoreAdminService.list();
    }

    @Permission("platform:jwt-keystore:page")
    @GetMapping("/wrap-settings")
    public JwtWrapSettingsVo wrapSettings() {
        return jwtKeystoreAdminService.wrapSettings();
    }

    @Permission("platform:jwt-keystore:rotate")
    @OperLog(value = "切换JWT包装密钥来源", eventType = OperLogEventTypes.CONFIG_CHANGE)
    @PutMapping("/wrap-settings")
    public JwtWrapSettingsVo saveWrapSettings(@RequestBody JwtWrapSourceSave body) {
        return jwtKeystoreAdminService.saveWrapSource(body == null ? null : body.getSource());
    }

    @Permission("platform:jwt-keystore:rotate")
    @OperLog(value = "生成JWT密钥", eventType = OperLogEventTypes.CONFIG_CHANGE)
    @PostMapping("/generate")
    public JwtKeystoreVo generate() {
        return jwtKeystoreAdminService.generate();
    }

    @Permission("platform:jwt-keystore:rotate")
    @OperLog(value = "激活JWT签发密钥", eventType = OperLogEventTypes.CONFIG_CHANGE)
    @PostMapping("/{kid}/activate")
    public JwtKeystoreVo activate(@PathVariable("kid") String kid) {
        return jwtKeystoreAdminService.activate(kid);
    }

    @Permission("platform:jwt-keystore:rotate")
    @OperLog(value = "摘除JWT密钥", eventType = OperLogEventTypes.CONFIG_CHANGE)
    @PostMapping("/{kid}/retire")
    public JwtKeystoreVo retire(@PathVariable("kid") String kid) {
        return jwtKeystoreAdminService.retire(kid);
    }
}
