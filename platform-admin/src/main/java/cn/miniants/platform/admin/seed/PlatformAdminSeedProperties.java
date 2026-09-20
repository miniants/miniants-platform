package cn.miniants.platform.admin.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 建完表之后库里一个账号都没有，进不去。这里配一个用来首次登录的管理员。
 *
 * <p>口令没有默认值：开源基座内置默认口令，等于每个照抄的项目都带同一个后门。
 */
@ConfigurationProperties(prefix = "platform.admin.seed")
public class PlatformAdminSeedProperties {

    /** 缺省关。只在初始化一套新库时打开，之后应从配置里去掉。 */
    private boolean enabled;

    private String username = "admin";

    /** 明文，启动时编码入库。开了种子就必须给，否则启动即失败。 */
    private String password;

    private String displayName = "系统管理员";

    private final Client client = new Client();

    /** 自建授权服务器时，同时建一个能换票的客户端；不配 {@code clientId} 就跳过。 */
    public static class Client {

        private String clientId;

        private String clientSecret;

        private String clientName = "默认客户端";

        private String grantTypes = "password,refresh_token";

        private String scopes = "all";

        private int accessTokenTtl = 3600;

        private int refreshTokenTtl = 86400;

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getClientName() {
            return clientName;
        }

        public void setClientName(String clientName) {
            this.clientName = clientName;
        }

        public String getGrantTypes() {
            return grantTypes;
        }

        public void setGrantTypes(String grantTypes) {
            this.grantTypes = grantTypes;
        }

        public String getScopes() {
            return scopes;
        }

        public void setScopes(String scopes) {
            this.scopes = scopes;
        }

        public int getAccessTokenTtl() {
            return accessTokenTtl;
        }

        public void setAccessTokenTtl(int accessTokenTtl) {
            this.accessTokenTtl = accessTokenTtl;
        }

        public int getRefreshTokenTtl() {
            return refreshTokenTtl;
        }

        public void setRefreshTokenTtl(int refreshTokenTtl) {
            this.refreshTokenTtl = refreshTokenTtl;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Client getClient() {
        return client;
    }
}
