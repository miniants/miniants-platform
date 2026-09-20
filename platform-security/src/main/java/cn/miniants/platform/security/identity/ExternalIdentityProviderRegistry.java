package cn.miniants.platform.security.identity;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按 {@link ExternalIdentityProvider#id()} 索引外部身份解析器。
 */
public final class ExternalIdentityProviderRegistry {

    private final Map<String, ExternalIdentityProvider> byId;

    public ExternalIdentityProviderRegistry(List<ExternalIdentityProvider> providers) {
        if (providers == null || providers.isEmpty()) {
            throw new IllegalStateException("至少需要一个 ExternalIdentityProvider");
        }
        Map<String, ExternalIdentityProvider> map = new LinkedHashMap<>();
        for (ExternalIdentityProvider provider : providers) {
            String id = provider.id();
            if (id == null || id.isBlank()) {
                throw new IllegalStateException(
                        provider.getClass().getName() + " 未实现 ExternalIdentityProvider.id()");
            }
            String key = id.trim();
            if (map.containsKey(key)) {
                throw new IllegalStateException("重复的 ExternalIdentityProvider.id(): " + key);
            }
            map.put(key, provider);
        }
        this.byId = Map.copyOf(map);
    }

    public ExternalIdentityProvider require(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("外部身份 provider 不能为空");
        }
        ExternalIdentityProvider provider = byId.get(providerId.trim());
        if (provider == null) {
            throw new IllegalArgumentException("未知外部身份 provider");
        }
        return provider;
    }

    /**
     * 配置未指定 provider 且容器内仅有一个实现时返回该实现；否则按 id 查找。
     */
    public ExternalIdentityProvider resolveConfigured(String configuredProviderId) {
        if (configuredProviderId != null && !configuredProviderId.isBlank()) {
            return require(configuredProviderId);
        }
        if (byId.size() == 1) {
            return byId.values().iterator().next();
        }
        throw new IllegalStateException(
                "存在多个 ExternalIdentityProvider，须配置 platform.security.bff.external.audiences.{audience}.provider");
    }

    public Collection<ExternalIdentityProvider> all() {
        return byId.values();
    }
}
