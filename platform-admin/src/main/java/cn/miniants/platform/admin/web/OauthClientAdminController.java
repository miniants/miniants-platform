package cn.miniants.platform.admin.web;

import cn.miniants.platform.admin.dto.OauthClientSave;
import cn.miniants.platform.admin.dto.OauthClientVo;
import cn.miniants.platform.admin.dto.PageResult;
import cn.miniants.platform.admin.oauth.OauthCatalog;
import cn.miniants.platform.admin.service.OauthClientAdminService;
import cn.miniants.platform.security.OperLog;
import cn.miniants.platform.security.OperLogEventTypes;
import cn.miniants.platform.security.Permission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

@RestController
@RequestMapping("/platform/admin/oauth-client")
public class OauthClientAdminController {

    private final OauthClientAdminService oauthClientAdminService;
    private final List<OauthCatalog> oauthCatalogs;

    public OauthClientAdminController(
            OauthClientAdminService oauthClientAdminService, List<OauthCatalog> oauthCatalogs) {
        this.oauthClientAdminService = oauthClientAdminService;
        this.oauthCatalogs = oauthCatalogs == null ? List.of() : List.copyOf(oauthCatalogs);
    }

    @Permission("platform:oauth-client:page")
    @GetMapping("/page")
    public PageResult<OauthClientVo> page(
            @RequestParam(name = "current", required = false) Long current,
            @RequestParam(name = "size", required = false) Long size,
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(name = "order", required = false) String order) {
        return oauthClientAdminService.page(current, size, filter, order);
    }

    @Permission("platform:oauth-client:page")
    @GetMapping("/{id}")
    public OauthClientVo get(@PathVariable("id") Long id) {
        return oauthClientAdminService.get(id);
    }

    @Permission("platform:oauth-client:page")
    @GetMapping("/grant-types")
    public List<String> grantTypes() {
        return mergeCatalog(OauthCatalog::grantTypes);
    }

    @Permission("platform:oauth-client:page")
    @GetMapping("/scopes")
    public List<String> scopes() {
        return mergeCatalog(OauthCatalog::scopes);
    }

    @Permission("platform:oauth-client:save")
    @OperLog(value = "保存客户端", eventType = OperLogEventTypes.OAUTH_CLIENT)
    @PostMapping("/save")
    public OauthClientVo save(@Valid @RequestBody OauthClientSave body) {
        return oauthClientAdminService.save(body);
    }

    @Permission("platform:oauth-client:save")
    @OperLog(value = "重置客户端密钥", eventType = OperLogEventTypes.OAUTH_CLIENT)
    @PostMapping("/{id}/reset-secret")
    public OauthClientVo resetSecret(@PathVariable("id") Long id) {
        return oauthClientAdminService.resetSecret(id);
    }

    @Permission("platform:oauth-client:delete")
    @OperLog(value = "删除客户端", eventType = OperLogEventTypes.OAUTH_CLIENT)
    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable("id") Long id) {
        oauthClientAdminService.delete(id);
        return Boolean.TRUE;
    }

    private List<String> mergeCatalog(CatalogValues values) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        for (OauthCatalog catalog : oauthCatalogs) {
            Collection<String> items = values.get(catalog);
            if (items == null) {
                continue;
            }
            items.stream()
                    .filter(item -> item != null && !item.isBlank())
                    .map(String::trim)
                    .forEach(merged::add);
        }
        return List.copyOf(merged);
    }

    @FunctionalInterface
    private interface CatalogValues {
        Collection<String> get(OauthCatalog catalog);
    }
}
