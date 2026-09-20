package cn.miniants.platform.security.sas;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.security.sas")
public class PlatformSasProperties {

    private boolean enabled = false;
    private String issuer = "http://localhost:18080";
    private String tokenEndpoint = "/oauth/token";
    private String jwkSetEndpoint = "/rsa/publicKey";

    /**
     * 允许无 keystore 时每次启动生成临时 RSA、授权存内存。默认 false；仅 demo / 单测显式开启。
     */
    private boolean allowEphemeralKeys = false;

    /**
     * 公开 {@code /oauth/token} 拒绝的 grant。BFF 进程内换票不受此限制。
     *
     * <p>内核只默认拒 {@code password}——它是内核唯一实现的自定义 grant，凭密码直换票必须走门面。
     * 项目自己加的 grant（微信、统一身份等）由项目往这里追加，内核不预置产品特有的名字。
     */
    private java.util.List<String> publicDeniedGrants = java.util.List.of("password", "external", "qr");

    /**
     * 管理端生成新钥时的 kid 前缀，拼成 {@code <prefix>-<yyyyMMddHHmmss>}。
     * 空则退回 keystore.alias 再拼时间戳，再空则随机。本层不生成密钥，仅暴露配置。
     * 见 {@link cn.miniants.platform.security.sas.keystore.JwtKids}。
     */
    private String kidPrefix = "";

    /**
     * 可选 JKS。配齐后用固定钥签名；不配则每次启动临时生成 RSA（仅适合单机 demo）。
     */
    private final Keystore keystore = new Keystore();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    public void setTokenEndpoint(String tokenEndpoint) {
        this.tokenEndpoint = tokenEndpoint;
    }

    public String getJwkSetEndpoint() {
        return jwkSetEndpoint;
    }

    public void setJwkSetEndpoint(String jwkSetEndpoint) {
        this.jwkSetEndpoint = jwkSetEndpoint;
    }

    public boolean isAllowEphemeralKeys() {
        return allowEphemeralKeys;
    }

    public void setAllowEphemeralKeys(boolean allowEphemeralKeys) {
        this.allowEphemeralKeys = allowEphemeralKeys;
    }

    public java.util.List<String> getPublicDeniedGrants() {
        return publicDeniedGrants;
    }

    public void setPublicDeniedGrants(java.util.List<String> publicDeniedGrants) {
        this.publicDeniedGrants = publicDeniedGrants == null ? java.util.List.of() : publicDeniedGrants;
    }

    public String getKidPrefix() {
        return kidPrefix;
    }

    public void setKidPrefix(String kidPrefix) {
        this.kidPrefix = kidPrefix == null ? "" : kidPrefix;
    }

    public Keystore getKeystore() {
        return keystore;
    }

    public static class Keystore {
        private String location = "";
        private String storePassword = "";
        private String alias = "";
        private String keyPassword = "";

        public boolean isConfigured() {
            return location != null && !location.isBlank();
        }

        public void requireComplete() {
            if (!isConfigured()) {
                return;
            }
            if (storePassword == null || storePassword.isBlank()
                    || alias == null || alias.isBlank()
                    || keyPassword == null || keyPassword.isBlank()) {
                throw new IllegalStateException(
                        "platform.security.sas.keystore.location 已配置，须同时提供 store-password / alias / key-password");
            }
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location == null ? "" : location;
        }

        public String getStorePassword() {
            return storePassword;
        }

        public void setStorePassword(String storePassword) {
            this.storePassword = storePassword == null ? "" : storePassword;
        }

        public String getAlias() {
            return alias;
        }

        public void setAlias(String alias) {
            this.alias = alias == null ? "" : alias;
        }

        public String getKeyPassword() {
            return keyPassword;
        }

        public void setKeyPassword(String keyPassword) {
            this.keyPassword = keyPassword == null ? "" : keyPassword;
        }
    }
}
