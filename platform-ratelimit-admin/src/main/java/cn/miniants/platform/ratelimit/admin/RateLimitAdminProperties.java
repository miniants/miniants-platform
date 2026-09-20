package cn.miniants.platform.ratelimit.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code platform.ratelimit.admin.*}。
 */
@ConfigurationProperties(prefix = "platform.ratelimit.admin")
public class RateLimitAdminProperties {

    /** 管理端总开关，缺省关闭。 */
    private boolean enabled = false;

    /**
     * 单机本地管理：无 Redis 时允许 NoOp 发布并记 WARN。多实例必须保持 false。
     */
    private boolean standalone = false;

    /** 兼容旧配置；数据面对账间隔请用 {@code platform.ratelimit.refresh-interval}。 */
    private Duration refreshInterval = Duration.ofSeconds(30);

    private final Ui ui = new Ui();

    private final Flyway flyway = new Flyway();

    private final Live live = new Live();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isStandalone() {
        return standalone;
    }

    public void setStandalone(boolean standalone) {
        this.standalone = standalone;
    }

    public Duration getRefreshInterval() {
        return refreshInterval;
    }

    public void setRefreshInterval(Duration refreshInterval) {
        this.refreshInterval = refreshInterval;
    }

    public Ui getUi() {
        return ui;
    }

    public Flyway getFlyway() {
        return flyway;
    }

    public Live getLive() {
        return live;
    }

    public static class Ui {

        private boolean enabled = false;

        private String path = "/platform/ratelimit";

        /**
         * 采用方把 Bearer 放在 sessionStorage / localStorage 时的键。
         * 空则只靠 Cookie。JWY 管理端为 {@code stormwind_Authorization}。
         */
        private String authorizationStorageKey = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getAuthorizationStorageKey() {
            return authorizationStorageKey;
        }

        public void setAuthorizationStorageKey(String authorizationStorageKey) {
            this.authorizationStorageKey = authorizationStorageKey == null ? "" : authorizationStorageKey;
        }
    }

    public static class Flyway {

        /** 与 admin 同时开启时缺省启用。 */
        private boolean enabled = true;

        private String table = "ratelimit_schema_history";

        private String locations = "classpath:db/ratelimit-migration";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }

        public String getLocations() {
            return locations;
        }

        public void setLocations(String locations) {
            this.locations = locations;
        }
    }

    /**
     * 管理面实时历史：后台按间隔保存 {@code /buckets} 整帧，供进入控制台时预载。
     * 默认关闭，避免升级后额外 SCAN。
     */
    public static class Live {

        public static final int MAX_FRAMES = 120;

        private boolean enabled = false;

        private Duration sampleInterval = Duration.ofSeconds(30);

        private Duration retention = Duration.ofHours(1);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getSampleInterval() {
            return sampleInterval;
        }

        public void setSampleInterval(Duration sampleInterval) {
            this.sampleInterval = sampleInterval;
        }

        public Duration getRetention() {
            return retention;
        }

        public void setRetention(Duration retention) {
            this.retention = retention;
        }
    }
}
