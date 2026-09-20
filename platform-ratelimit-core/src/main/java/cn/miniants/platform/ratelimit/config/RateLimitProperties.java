package cn.miniants.platform.ratelimit.config;

import cn.miniants.platform.ratelimit.RateLimitAlgorithm;
import cn.miniants.platform.ratelimit.RateLimitBackend;
import cn.miniants.platform.ratelimit.RateLimitDurations;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.StoreFailurePolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code platform.ratelimit.*} 配置。
 */
@ConfigurationProperties(prefix = "platform.ratelimit")
public class RateLimitProperties {

    /**
     * 总开关；关闭时不注册本地限流 Bean（由调用方自行判断亦可）。
     */
    private boolean enabled = true;

    /**
     * 存储后端：{@code local} 或 {@code redis}。
     */
    private String backend = "local";

    /**
     * Redis / 本地键前缀。
     */
    private String keyPrefix = "platform:ratelimit:";

    /**
     * 本地策略快照相对最近一次成功读取策略源的最大陈旧时长。
     */
    private Duration staleSnapshotMaxAge = Duration.ofMinutes(5);

    /**
     * Redis 策略快照与修订键 TTL；周期对账会续期，避免永久键。
     */
    private Duration snapshotTtl = Duration.ofMinutes(10);

    /**
     * 数据面拉取 / 对账间隔。
     */
    private Duration refreshInterval = Duration.ofSeconds(30);

    /**
     * WebFlux 限流过滤器顺序。须大于 Spring Security WebFilter（默认 -100）。
     */
    private int webfluxFilterOrder = -50;

    /**
     * YAML 内置基线策略：code → 定义。
     */
    private Map<String, PolicyDefinition> policies = new LinkedHashMap<>();

    /**
     * 可信反向代理 CIDR 列表。仅当直连 peer 命中时才解析 Forwarded / X-Forwarded-For。
     */
    private List<String> trustedProxies = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBackend() {
        return backend;
    }

    public void setBackend(String backend) {
        this.backend = backend;
    }

    public RateLimitBackend backendType() {
        if (backend == null || backend.isBlank()) {
            return RateLimitBackend.LOCAL;
        }
        return switch (backend.trim().toLowerCase(Locale.ROOT)) {
            case "local" -> RateLimitBackend.LOCAL;
            case "redis" -> RateLimitBackend.REDIS;
            default -> throw new IllegalArgumentException("不支持的限流后端: " + backend);
        };
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public Duration getStaleSnapshotMaxAge() {
        return staleSnapshotMaxAge;
    }

    public void setStaleSnapshotMaxAge(Duration staleSnapshotMaxAge) {
        this.staleSnapshotMaxAge = staleSnapshotMaxAge;
    }

    public Duration getSnapshotTtl() {
        return snapshotTtl;
    }

    public void setSnapshotTtl(Duration snapshotTtl) {
        this.snapshotTtl = snapshotTtl;
    }

    public Duration getRefreshInterval() {
        return refreshInterval;
    }

    public void setRefreshInterval(Duration refreshInterval) {
        this.refreshInterval = refreshInterval;
    }

    public int getWebfluxFilterOrder() {
        return webfluxFilterOrder;
    }

    public void setWebfluxFilterOrder(int webfluxFilterOrder) {
        this.webfluxFilterOrder = webfluxFilterOrder;
    }

    public Map<String, PolicyDefinition> getPolicies() {
        return policies;
    }

    public void setPolicies(Map<String, PolicyDefinition> policies) {
        this.policies = policies == null ? new LinkedHashMap<>() : policies;
    }

    public List<String> getTrustedProxies() {
        return trustedProxies;
    }

    public void setTrustedProxies(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies == null ? new ArrayList<>() : trustedProxies;
    }

    public static class PolicyDefinition {

        private RateLimitAlgorithm algorithm = RateLimitAlgorithm.GCRA;
        private long limit = 1;
        /**
         * 简写（1s/1m/1h/1d）或 ISO-8601；也可用 Spring Duration 绑定结果。
         */
        private String period = "1m";
        private Long burst;
        private StoreFailurePolicy storeFailurePolicy = StoreFailurePolicy.DENY;
        private boolean enabled = true;
        private long version;

        public RateLimitAlgorithm getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(RateLimitAlgorithm algorithm) {
            this.algorithm = algorithm;
        }

        public long getLimit() {
            return limit;
        }

        public void setLimit(long limit) {
            this.limit = limit;
        }

        public String getPeriod() {
            return period;
        }

        public void setPeriod(String period) {
            this.period = period;
        }

        public Long getBurst() {
            return burst;
        }

        public void setBurst(Long burst) {
            this.burst = burst;
        }

        public StoreFailurePolicy getStoreFailurePolicy() {
            return storeFailurePolicy;
        }

        public void setStoreFailurePolicy(StoreFailurePolicy storeFailurePolicy) {
            this.storeFailurePolicy = storeFailurePolicy;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getVersion() {
            return version;
        }

        public void setVersion(long version) {
            this.version = version;
        }

        public RateLimitPolicy toPolicy(String policyCode) {
            RateLimitPolicy.Builder builder = RateLimitPolicy.builder()
                    .policyCode(policyCode)
                    .algorithm(algorithm == null ? RateLimitAlgorithm.GCRA : algorithm)
                    .limit(limit)
                    .period(RateLimitDurations.parse(period))
                    .storeFailurePolicy(
                            storeFailurePolicy == null ? StoreFailurePolicy.DENY : storeFailurePolicy)
                    .enabled(enabled)
                    .version(version);
            if (burst != null) {
                builder.burst(burst);
            }
            return builder.build();
        }
    }
}
