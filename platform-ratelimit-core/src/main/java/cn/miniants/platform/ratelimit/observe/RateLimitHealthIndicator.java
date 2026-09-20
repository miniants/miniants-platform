package cn.miniants.platform.ratelimit.observe;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * 限流运行时健康：backend、revision、刷新时间、陈旧与发布失败。
 */
public class RateLimitHealthIndicator implements HealthIndicator {

    private final RateLimitRuntimeInspector inspector;

    public RateLimitHealthIndicator(RateLimitRuntimeInspector inspector) {
        this.inspector = inspector;
    }

    @Override
    public Health health() {
        RateLimitRuntimeSnapshot snapshot = inspector.snapshot();
        Health.Builder builder = snapshot.snapshotStale() || snapshot.sourceUnavailable() || snapshot.publishFailed()
                ? Health.down()
                : Health.up();
        return builder
                .withDetail("backend", snapshot.backend())
                .withDetail("revision", snapshot.revision())
                .withDetail("lastSuccessfulRefreshAt", snapshot.lastSuccessfulRefreshAt())
                .withDetail("snapshotAgeMs", snapshot.snapshotAgeMs())
                .withDetail("stale", snapshot.snapshotStale())
                .withDetail("sourceUnavailable", snapshot.sourceUnavailable())
                .withDetail("publishFailed", snapshot.publishFailed())
                .withDetail("availablePolicies", snapshot.availablePolicies())
                .build();
    }
}
