package cn.miniants.platform.ratelimit.admin.dto;

import java.time.Instant;
import java.util.List;

public class RateLimitBucketsVo {

    private Instant observedAt;
    private String backend;
    private boolean truncated;
    private List<RateLimitBucketVo> buckets;

    public Instant getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(Instant observedAt) {
        this.observedAt = observedAt;
    }

    public String getBackend() {
        return backend;
    }

    public void setBackend(String backend) {
        this.backend = backend;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public List<RateLimitBucketVo> getBuckets() {
        return buckets;
    }

    public void setBuckets(List<RateLimitBucketVo> buckets) {
        this.buckets = buckets;
    }
}
