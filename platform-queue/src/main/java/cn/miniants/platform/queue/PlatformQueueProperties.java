package cn.miniants.platform.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.queue")
public class PlatformQueueProperties {

    /**
     * 队列 Redis 键前缀，须以 {@code :} 结尾，例如 {@code myapp:queue:}。
     *
     * <p>不配就不注册 {@link QueueKeyFactory}，由项目自己 new——前缀写错等于换了一套键，
     * 在途任务会全部变成孤儿，用编译期常量比用配置安全。只有确实要按环境切前缀时才配这个。
     */
    private String keyPrefix;

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }
}
