package cn.miniants.platform.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.admin.api")
public class PlatformAdminApiProperties {

    /**
     * 关掉参考 CRUD Controller。Service 仍可被业务替换后自挂接口。
     */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
