package cn.miniants.platform.ratelimit;

import java.time.Duration;
import java.util.Objects;

/**
 * 不可变限流策略。
 *
 * <p>{@code burst} 缺省等于 {@code limit}（GCRA 满桶容量）；滑动窗口忽略 burst。
 */
public record RateLimitPolicy(
        String policyCode,
        RateLimitAlgorithm algorithm,
        long limit,
        Duration period,
        long burst,
        StoreFailurePolicy storeFailurePolicy,
        boolean enabled,
        long version
) {

    public RateLimitPolicy {
        Objects.requireNonNull(policyCode, "policyCode");
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(storeFailurePolicy, "storeFailurePolicy");
        if (policyCode.isBlank()) {
            throw new IllegalArgumentException("策略编码不能为空");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("限流阈值必须为正数");
        }
        if (period.isZero() || period.isNegative()) {
            throw new IllegalArgumentException("限流周期必须为正");
        }
        if (burst <= 0) {
            throw new IllegalArgumentException("突发容量必须为正数");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String policyCode;
        private RateLimitAlgorithm algorithm = RateLimitAlgorithm.GCRA;
        private long limit;
        private Duration period;
        private Long burst;
        private StoreFailurePolicy storeFailurePolicy = StoreFailurePolicy.DENY;
        private boolean enabled = true;
        private long version;

        public Builder policyCode(String policyCode) {
            this.policyCode = policyCode;
            return this;
        }

        public Builder algorithm(RateLimitAlgorithm algorithm) {
            this.algorithm = algorithm;
            return this;
        }

        public Builder limit(long limit) {
            this.limit = limit;
            return this;
        }

        public Builder period(Duration period) {
            this.period = period;
            return this;
        }

        public Builder burst(long burst) {
            this.burst = burst;
            return this;
        }

        public Builder storeFailurePolicy(StoreFailurePolicy storeFailurePolicy) {
            this.storeFailurePolicy = storeFailurePolicy;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder version(long version) {
            this.version = version;
            return this;
        }

        public RateLimitPolicy build() {
            long effectiveBurst = burst != null ? burst : limit;
            return new RateLimitPolicy(
                    policyCode,
                    algorithm,
                    limit,
                    period,
                    effectiveBurst,
                    storeFailurePolicy,
                    enabled,
                    version);
        }
    }
}
