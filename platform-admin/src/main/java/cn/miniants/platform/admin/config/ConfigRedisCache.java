package cn.miniants.platform.admin.config;

/**
 * 配置共享缓存的可选 Redis 适配。接口不出现 Redis 类型，避免无 Redis 的采用方加载失败。
 */
public interface ConfigRedisCache {

    String get(String key);

    void set(String key, String value);

    void delete(String key);
}
