package cn.miniants.platform.admin.keystore;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 密钥库管理配置。包装密钥也可用环境变量 {@link AesGcmPrivateKeyWrap#ENV_WRAP_KEY}。
 */
@ConfigurationProperties(prefix = "platform.admin.jwt")
public class PlatformJwtKeystoreProperties {

    /**
     * AES-256 包装密钥（Base64）。{@code wrap-source=env} 时使用；空则回退 {@code PLATFORM_JWT_WRAP_KEY}。
     */
    private String wrapKey = "";

    /**
     * 包装密钥来源：{@code env}（宿主机 / 配置）或 {@code database}（本库自管）。
     * 库内若已有设置行，以库为准。
     */
    private String wrapSource = JwtWrapSources.ENV;

    public String getWrapKey() {
        return wrapKey;
    }

    public void setWrapKey(String wrapKey) {
        this.wrapKey = wrapKey == null ? "" : wrapKey;
    }

    public String getWrapSource() {
        return wrapSource;
    }

    public void setWrapSource(String wrapSource) {
        this.wrapSource = wrapSource == null ? JwtWrapSources.ENV : wrapSource;
    }
}
