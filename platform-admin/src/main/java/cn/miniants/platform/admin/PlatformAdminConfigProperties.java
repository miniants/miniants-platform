package cn.miniants.platform.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.admin.config")
public class PlatformAdminConfigProperties {

    /**
     * {@code sys_config} 共享缓存键前缀。采用方可改到自己的根下。
     */
    private String redisKeyPrefix = "platform:config:";

    public String getRedisKeyPrefix() {
        return redisKeyPrefix;
    }

    public void setRedisKeyPrefix(String redisKeyPrefix) {
        this.redisKeyPrefix = redisKeyPrefix == null || redisKeyPrefix.isBlank()
                ? "platform:config:"
                : normalizePrefix(redisKeyPrefix);
    }

    static String normalizePrefix(String prefix) {
        String trimmed = prefix.trim();
        return trimmed.endsWith(":") ? trimmed : trimmed + ":";
    }
}
