package cn.miniants.platform.ratelimit.redis.snapshot;

import org.springframework.scheduling.annotation.Scheduled;

/**
 * Pub/Sub 丢失时的周期对账，并续期快照 TTL。
 */
public class RateLimitPolicySnapshotReconcileScheduler {

    private final RateLimitPolicySnapshotRefreshService refreshService;

    public RateLimitPolicySnapshotReconcileScheduler(RateLimitPolicySnapshotRefreshService refreshService) {
        this.refreshService = refreshService;
    }

    @Scheduled(fixedDelayString = "${platform.ratelimit.refresh-interval:30s}")
    public void poll() {
        refreshService.refreshFromRedis();
    }
}
