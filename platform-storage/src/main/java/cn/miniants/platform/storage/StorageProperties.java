package cn.miniants.platform.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "platform.storage")
public class StorageProperties {

    /**
     * 按名字配置多个后端。名字即 bean 名；不指定 {@link #primary} 时用声明顺序的第一个，
     * 所以这里保持 {@link LinkedHashMap}。
     */
    private Map<String, StorageBackendProperties> backends = new LinkedHashMap<>();

    /** 注入 {@code ObjectStorage} 时拿到哪一个。 */
    private String primary;

    public Map<String, StorageBackendProperties> getBackends() {
        return backends;
    }

    public void setBackends(Map<String, StorageBackendProperties> backends) {
        this.backends = backends;
    }

    public String getPrimary() {
        return primary;
    }

    public void setPrimary(String primary) {
        this.primary = primary;
    }
}
