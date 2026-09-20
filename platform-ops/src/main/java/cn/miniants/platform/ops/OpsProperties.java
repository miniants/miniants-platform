package cn.miniants.platform.ops;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.ops")
public class OpsProperties {

    /**
     * 模块在 classpath 即开。现网五个应用不挂就零行为变化。
     */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
