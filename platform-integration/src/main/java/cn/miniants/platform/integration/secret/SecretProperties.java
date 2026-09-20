package cn.miniants.platform.integration.secret;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "platform.secret")
public class SecretProperties {

    private Map<String, String> values = new LinkedHashMap<>();

    public Map<String, String> getValues() {
        return values;
    }

    public void setValues(Map<String, String> values) {
        this.values = values == null ? new LinkedHashMap<>() : values;
    }
}
