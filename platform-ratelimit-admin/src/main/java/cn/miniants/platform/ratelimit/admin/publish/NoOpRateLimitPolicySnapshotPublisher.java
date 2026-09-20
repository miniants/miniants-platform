package cn.miniants.platform.ratelimit.admin.publish;

import cn.miniants.platform.ratelimit.redis.snapshot.RateLimitPolicySnapshotPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 单机模式空发布器：只更新本机注册表，不同步其他实例。
 */
public class NoOpRateLimitPolicySnapshotPublisher implements RateLimitPolicySnapshotPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoOpRateLimitPolicySnapshotPublisher.class);

    @Override
    public int publish(long revision, String snapshotJson) {
        log.warn("限流管理面处于单机模式，跳过远程发布 revision={}", revision);
        return 1;
    }
}
