package cn.miniants.platform.ratelimit.admin.web;

import cn.miniants.platform.core.api.RawBody;
import cn.miniants.platform.ratelimit.admin.RateLimitAdminProperties;
import cn.miniants.platform.security.PublicAccess;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 控制台同源运行时配置，避免前端用相对路径推导 API。
 */
@RestController
@RawBody
@ConditionalOnProperty(prefix = "platform.ratelimit.admin.ui", name = "enabled", havingValue = "true")
public class RateLimitAdminUiConfigController {

    private final RateLimitAdminProperties properties;

    public RateLimitAdminUiConfigController(RateLimitAdminProperties properties) {
        this.properties = properties;
    }

    @PublicAccess
    @GetMapping("${platform.ratelimit.admin.ui.path:/platform/ratelimit}/config.json")
    public Map<String, String> config(HttpServletRequest request) {
        String context = request.getContextPath() == null ? "" : request.getContextPath();
        String uiPath = normalize(properties.getUi().getPath());
        Map<String, String> config = new LinkedHashMap<>();
        // 相对当前页（须以 / 结尾）：经 /jwy-api 或 Vite 前缀时仍打到同一前缀下的管理 API
        config.put("apiBase", "../admin/rate-limit/policies");
        config.put("contextPath", context);
        config.put("uiPath", context + uiPath);
        String storageKey = properties.getUi().getAuthorizationStorageKey();
        if (storageKey != null && !storageKey.isBlank()) {
            config.put("authorizationStorageKey", storageKey.trim());
        }
        return config;
    }

    private static String normalize(String path) {
        if (path == null || path.isBlank()) {
            return "/platform/ratelimit";
        }
        String trimmed = path.startsWith("/") ? path : "/" + path;
        if (trimmed.endsWith("/") && trimmed.length() > 1) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
