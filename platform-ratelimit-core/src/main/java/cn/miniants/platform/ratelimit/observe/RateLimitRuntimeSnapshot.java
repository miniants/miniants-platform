package cn.miniants.platform.ratelimit.observe;

import java.time.Instant;
import java.util.List;

/**
 * Actuator / Health 只读视图，不暴露 subject 或 Redis key。
 */
public record RateLimitRuntimeSnapshot(
        String backend,
        long revision,
        Instant lastSuccessfulRefreshAt,
        Long snapshotAgeMs,
        boolean snapshotStale,
        boolean sourceUnavailable,
        boolean publishFailed,
        List<String> availablePolicies) {
}
