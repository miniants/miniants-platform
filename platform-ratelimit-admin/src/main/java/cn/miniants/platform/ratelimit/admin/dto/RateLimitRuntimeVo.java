package cn.miniants.platform.ratelimit.admin.dto;

import java.time.Instant;
import java.util.List;

public class RateLimitRuntimeVo {

    private long revision;
    private Instant snapshotLoadedAt;
    private Long snapshotAgeMs;
    private Instant lastSuccessfulRefreshAt;
    private boolean snapshotStale;
    private boolean sourceUnavailable;
    private boolean publishFailed;
    private String backend;
    private List<String> availablePolicies;
    private List<RateLimitEffectivePolicyVo> effectivePolicies;

    public long getRevision() {
        return revision;
    }

    public void setRevision(long revision) {
        this.revision = revision;
    }

    public Instant getSnapshotLoadedAt() {
        return snapshotLoadedAt;
    }

    public void setSnapshotLoadedAt(Instant snapshotLoadedAt) {
        this.snapshotLoadedAt = snapshotLoadedAt;
    }

    public Instant getLastSuccessfulRefreshAt() {
        return lastSuccessfulRefreshAt;
    }

    public void setLastSuccessfulRefreshAt(Instant lastSuccessfulRefreshAt) {
        this.lastSuccessfulRefreshAt = lastSuccessfulRefreshAt;
    }

    public Long getSnapshotAgeMs() {
        return snapshotAgeMs;
    }

    public void setSnapshotAgeMs(Long snapshotAgeMs) {
        this.snapshotAgeMs = snapshotAgeMs;
    }

    public boolean isSnapshotStale() {
        return snapshotStale;
    }

    public void setSnapshotStale(boolean snapshotStale) {
        this.snapshotStale = snapshotStale;
    }

    public boolean isSourceUnavailable() {
        return sourceUnavailable;
    }

    public void setSourceUnavailable(boolean sourceUnavailable) {
        this.sourceUnavailable = sourceUnavailable;
    }

    public boolean isPublishFailed() {
        return publishFailed;
    }

    public void setPublishFailed(boolean publishFailed) {
        this.publishFailed = publishFailed;
    }

    public String getBackend() {
        return backend;
    }

    public void setBackend(String backend) {
        this.backend = backend;
    }

    public List<String> getAvailablePolicies() {
        return availablePolicies;
    }

    public void setAvailablePolicies(List<String> availablePolicies) {
        this.availablePolicies = availablePolicies;
    }

    public List<RateLimitEffectivePolicyVo> getEffectivePolicies() {
        return effectivePolicies;
    }

    public void setEffectivePolicies(List<RateLimitEffectivePolicyVo> effectivePolicies) {
        this.effectivePolicies = effectivePolicies;
    }
}
