package cn.miniants.platform.security.bff;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "platform.security.bff")
public class PlatformBffProperties {

    /**
     * 第一方 Web 门面。secret 留服务端。
     */
    private boolean enabled = false;

    /**
     * 门面代换票用的机密客户端。不要把 secret 配进前端。
     */
    private String clientId = "";

    private final RealName realName = new RealName();
    private final External external = new External();
    private final Qr qr = new Qr();
    private final Challenge challenge = new Challenge();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public RealName getRealName() {
        return realName;
    }

    public External getExternal() {
        return external;
    }

    public Qr getQr() {
        return qr;
    }

    public Challenge getChallenge() {
        return challenge;
    }

    public static class RealName {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class External {
        private boolean enabled = false;
        private Map<String, Audience> audiences = new LinkedHashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Map<String, Audience> getAudiences() {
            return audiences;
        }

        public void setAudiences(Map<String, Audience> audiences) {
            this.audiences = audiences == null ? new LinkedHashMap<>() : new LinkedHashMap<>(audiences);
        }

        public Audience requireAudience(String audience) {
            if (audience == null || audience.isBlank()) {
                throw new IllegalArgumentException("外部身份 audience 不能为空");
            }
            Audience cfg = audiences.get(audience.trim());
            if (cfg == null || cfg.getClientId() == null || cfg.getClientId().isBlank()) {
                throw new IllegalArgumentException("未知外部身份 audience");
            }
            return cfg;
        }
    }

    public static class Audience {
        private String clientId = "";
        /** 可选；省略且仅有一个 Provider 时用其 {@link cn.miniants.platform.security.identity.ExternalIdentityProvider#id()} */
        private String provider = "";

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }
    }

    public static class Qr {
        private boolean enabled = false;
        private String redisKeyPrefix = "platform:auth:qr:";
        private String confirmChannel = "miniapp";
        /** 确认端按 person 查外部身份绑定的 provider，不得与发起端 clientId 混用。 */
        private String confirmProvider = "";
        private Map<String, Channel> channels = new LinkedHashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getRedisKeyPrefix() {
            return redisKeyPrefix;
        }

        public void setRedisKeyPrefix(String redisKeyPrefix) {
            this.redisKeyPrefix = redisKeyPrefix == null || redisKeyPrefix.isBlank()
                    ? "platform:auth:qr:"
                    : redisKeyPrefix;
        }

        public String getConfirmChannel() {
            return confirmChannel;
        }

        public void setConfirmChannel(String confirmChannel) {
            this.confirmChannel = confirmChannel;
        }

        public String getConfirmProvider() {
            return confirmProvider;
        }

        public void setConfirmProvider(String confirmProvider) {
            this.confirmProvider = confirmProvider;
        }

        public Map<String, Channel> getChannels() {
            return channels;
        }

        public void setChannels(Map<String, Channel> channels) {
            this.channels = channels == null ? new LinkedHashMap<>() : new LinkedHashMap<>(channels);
        }

        public String requireClientId(String channel) {
            if (channel == null || channel.isBlank()) {
                throw new IllegalArgumentException("扫码通道不能为空");
            }
            Channel cfg = channels.get(channel.trim());
            if (cfg == null || cfg.getClientId() == null || cfg.getClientId().isBlank()) {
                throw new IllegalArgumentException("未知扫码通道");
            }
            return cfg.getClientId().trim();
        }

        public String requireConfirmProvider() {
            if (confirmProvider == null || confirmProvider.isBlank()) {
                throw new IllegalStateException("platform.security.bff.qr.confirm-provider 未配置");
            }
            return confirmProvider.trim();
        }
    }

    public static class Channel {
        private String clientId = "";

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }
    }

    public static class Challenge {
        private boolean enabled = false;
        /**
         * 失败计数 / 滑块题 Redis 前缀。拼出 {@code {prefix}fail:} 与 {@code {prefix}slider:}。
         * 采用方可改到自己的根下以保持存量键。
         */
        private String redisKeyPrefix = "platform:auth:web-";
        /** 为 true 时 clientKey 取 X-Forwarded-For 首跳；默认 false，只用直连地址。 */
        private boolean trustForwardedFor = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getRedisKeyPrefix() {
            return redisKeyPrefix;
        }

        public void setRedisKeyPrefix(String redisKeyPrefix) {
            this.redisKeyPrefix = redisKeyPrefix == null || redisKeyPrefix.isBlank()
                    ? "platform:auth:web-"
                    : redisKeyPrefix;
        }

        public boolean isTrustForwardedFor() {
            return trustForwardedFor;
        }

        public void setTrustForwardedFor(boolean trustForwardedFor) {
            this.trustForwardedFor = trustForwardedFor;
        }
    }
}
