package cn.miniants.platform.ratelimit.admin.dto;

import java.time.LocalDateTime;

public class RateLimitPolicyVo {

    private Long id;
    private String policyCode;
    private String algorithm;
    private Long limitCount;
    private Long periodMs;
    private Long burst;
    private String storeFailurePolicy;
    private Boolean enabled;
    private Long version;
    private String remark;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public Long getLimitCount() {
        return limitCount;
    }

    public void setLimitCount(Long limitCount) {
        this.limitCount = limitCount;
    }

    public Long getPeriodMs() {
        return periodMs;
    }

    public void setPeriodMs(Long periodMs) {
        this.periodMs = periodMs;
    }

    public Long getBurst() {
        return burst;
    }

    public void setBurst(Long burst) {
        this.burst = burst;
    }

    public String getStoreFailurePolicy() {
        return storeFailurePolicy;
    }

    public void setStoreFailurePolicy(String storeFailurePolicy) {
        this.storeFailurePolicy = storeFailurePolicy;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
