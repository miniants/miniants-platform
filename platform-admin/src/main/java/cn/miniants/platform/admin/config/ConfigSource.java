package cn.miniants.platform.admin.config;

import java.util.Optional;

/**
 * 运行期 {@code sys_config} 读写。按 category + key 点查，不要整表下发。
 */
public interface ConfigSource {

    Optional<String> get(String category, String key, ConfigLookup lookup);

    default String getOrDefault(String category, String key, String defaultValue, ConfigLookup lookup) {
        return get(category, key, lookup).filter(value -> !value.isBlank()).orElse(defaultValue);
    }

    void set(String category, String key, String value, ConfigWrite write);

    /** 删 Redis / 本地缓存并广播。管理端改行后必须调用。 */
    void evict(String category, String key);
}
