package cn.miniants.platform.ratelimit.admin.dto;

public class RateLimitBucketVo {

    private String policyCode;
    private String subject;
    private String algorithm;
    private long limitCount;
    private long remaining;
    private long retryAfterMs;
    private long resetAtMs;

    public String getPolicyCode() {
        return policyCode;
    }

    public void setPolicyCode(String policyCode) {
        this.policyCode = policyCode;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
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

    public long getRemaining() {
        return remaining;
    }

    public void setRemaining(long remaining) {
        this.remaining = remaining;
    }

    public long getRetryAfterMs() {
        return retryAfterMs;
    }

    public void setRetryAfterMs(long retryAfterMs) {
        this.retryAfterMs = retryAfterMs;
    }

    public long getResetAtMs() {
        return resetAtMs;
    }

    public void setResetAtMs(long resetAtMs) {
        this.resetAtMs = resetAtMs;
    }
}
