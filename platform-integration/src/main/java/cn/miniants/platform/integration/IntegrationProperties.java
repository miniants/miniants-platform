package cn.miniants.platform.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.integration")
public class IntegrationProperties {

    private boolean enabled = true;

    private final Redis redis = new Redis();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Redis getRedis() {
        return redis;
    }

    public static class Redis {

        private boolean enabled = true;

        /**
         * 锁 / 幂等键的公共前缀。同一 Redis 实例上多个应用共存时必须区分开。
         * 限流键空间见 {@code platform.ratelimit.key-prefix}。
         */
        private String keyPrefix = "platform:";

        /**
         * 失效总线的 Pub/Sub channel。留空按 {@code keyPrefix + "bus:invalidate"} 推。
         *
         * <p>同一应用的所有实例必须一致。已经在跑的应用不要改这个值：改了等于换 channel，
         * 滚动发布期间新老实例互相收不到，缓存不失效但不报错。
         */
        private String invalidateChannel;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }

        public String getInvalidateChannel() {
            return invalidateChannel;
        }

        public void setInvalidateChannel(String invalidateChannel) {
            this.invalidateChannel = invalidateChannel;
        }

        public String invalidateChannelOrDefault() {
            return invalidateChannel == null || invalidateChannel.isBlank()
                    ? keyPrefix + "bus:invalidate"
                    : invalidateChannel.trim();
        }
    }
}
