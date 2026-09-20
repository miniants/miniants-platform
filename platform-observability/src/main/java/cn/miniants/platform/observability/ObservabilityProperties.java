package cn.miniants.platform.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.observability")
public class ObservabilityProperties {

    private boolean enabled = true;
    private boolean accessLog = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAccessLog() {
        return accessLog;
    }

    public void setAccessLog(boolean accessLog) {
        this.accessLog = accessLog;
    }
}
