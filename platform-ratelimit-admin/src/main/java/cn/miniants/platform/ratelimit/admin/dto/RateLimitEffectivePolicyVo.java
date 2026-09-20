package cn.miniants.platform.ratelimit.admin.dto;

/**
 * 本机当前生效的命名策略（动态 &gt; YAML &gt; 内置），供控制台对照编码。
 */
public class RateLimitEffectivePolicyVo {

    private String policyCode;
    private String algorithm;
    private long limitCount;
    private long periodMs;
    private long burst;
    private String storeFailurePolicy;
    private boolean enabled;
    /** {@code dynamic} / {@code yaml} / {@code builtin} */
    private String source;

    public String getPolicyCode() {
        return policyCode;
    }

    public void setPolicyCode(String policyCode) {
        this.policyCode = policyCode;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public long getLimitCount() {
        return limitCount;
    }

    public void setLimitCount(long limitCount) {
        this.limitCount = limitCount;
    }

    public long getPeriodMs() {
        return periodMs;
    }

    public void setPeriodMs(long periodMs) {
        this.periodMs = periodMs;
    }

    public long getBurst() {
        return burst;
    }

    public void setBurst(long burst) {
        this.burst = burst;
    }

    public String getStoreFailurePolicy() {
        return storeFailurePolicy;
    }

    public void setStoreFailurePolicy(String storeFailurePolicy) {
        this.storeFailurePolicy = storeFailurePolicy;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
