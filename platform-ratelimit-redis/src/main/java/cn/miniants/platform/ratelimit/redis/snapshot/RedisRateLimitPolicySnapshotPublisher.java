package cn.miniants.platform.ratelimit.redis.snapshot;

import cn.miniants.platform.ratelimit.config.RateLimitProperties;
import cn.miniants.platform.ratelimit.redis.RateLimitRedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

/**
 * Redis 快照发布：Lua CAS 写入 snapshot/revision 并 PUBLISH。
 */
public class RedisRateLimitPolicySnapshotPublisher implements RateLimitPolicySnapshotPublisher {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitPolicySnapshotPublisher.class);

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;
    private final Duration snapshotTtl;

    public RedisRateLimitPolicySnapshotPublisher(
            StringRedisTemplate redisTemplate,
            RateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        String prefix = properties == null ? null : properties.getKeyPrefix();
        this.keyPrefix = prefix == null || prefix.isBlank() ? "platform:ratelimit:" : prefix;
        Duration ttl = properties == null ? null : properties.getSnapshotTtl();
        this.snapshotTtl = ttl == null || ttl.isZero() || ttl.isNegative()
                ? Duration.ofMinutes(10)
                : ttl;
    }

    @Override
    public int publish(long revision, String snapshotJson) {
        String revisionKey = RateLimitRedisKeys.policyRevision(keyPrefix);
        String snapshotKey = RateLimitRedisKeys.policySnapshot(keyPrefix);
        String channel = RateLimitRedisKeys.policyEventsChannel(keyPrefix);
        try {
            Long result = redisTemplate.execute(
                    RateLimitSnapshotScripts.PUBLISH,
                    List.of(revisionKey, snapshotKey, channel),
                    Long.toString(revision),
                    snapshotJson,
                    Long.toString(snapshotTtl.toMillis()));
            int code = result == null ? 0 : result.intValue();
            log.debug("发布限流策略快照 revision={} result={}", revision, code);
            return code;
        } catch (RuntimeException ex) {
            log.error("发布限流策略快照失败 revision={}", revision, ex);
            throw ex;
        }
    }
}
