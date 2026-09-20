package cn.miniants.platform.ratelimit.redis.snapshot;

/**
 * 将运行时策略快照写入 Redis 并广播修订号。
 *
 * @return 1 新修订已写入，2 相同修订已续期，0 被更新修订拒绝
 */
public interface RateLimitPolicySnapshotPublisher {

    int publish(long revision, String snapshotJson);
}
