package cn.miniants.platform.ratelimit.redis.snapshot;

import cn.miniants.platform.ratelimit.config.RateLimitProperties;
import cn.miniants.platform.ratelimit.policy.CompositeRateLimitPolicyRegistry;
import cn.miniants.platform.ratelimit.policy.DynamicPolicyApplyResult;
import cn.miniants.platform.ratelimit.redis.RateLimitRedisKeys;
import cn.miniants.platform.ratelimit.snapshot.RateLimitPolicySnapshot;
import cn.miniants.platform.ratelimit.snapshot.RateLimitPolicySnapshotCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;

/**
 * 数据面：从 Redis 拉取运行时快照并 CAS 写入本地注册表。
 */
public class RateLimitPolicySnapshotRefreshService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitPolicySnapshotRefreshService.class);

    private final StringRedisTemplate redisTemplate;
    private final RateLimitPolicySnapshotCodec codec;
    private final CompositeRateLimitPolicyRegistry registry;
    private final String keyPrefix;
    private final Duration snapshotTtl;

    public RateLimitPolicySnapshotRefreshService(
            StringRedisTemplate redisTemplate,
            RateLimitPolicySnapshotCodec codec,
            CompositeRateLimitPolicyRegistry registry,
            RateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.codec = codec;
        this.registry = registry;
        String prefix = properties == null ? null : properties.getKeyPrefix();
        this.keyPrefix = prefix == null || prefix.isBlank() ? "platform:ratelimit:" : prefix;
        Duration ttl = properties == null ? null : properties.getSnapshotTtl();
        this.snapshotTtl = ttl == null || ttl.isZero() || ttl.isNegative()
                ? Duration.ofMinutes(10)
                : ttl;
    }

    public boolean refreshFromRedis() {
        String snapshotKey = RateLimitRedisKeys.policySnapshot(keyPrefix);
        String json;
        try {
            json = redisTemplate.opsForValue().get(snapshotKey);
        } catch (RuntimeException ex) {
            log.warn("读取限流策略快照失败: {}", ex.getMessage());
            registry.markSourceUnavailable(true);
            return false;
        }
        if (json == null || json.isBlank()) {
            registry.markSourceUnavailable(true);
            return false;
        }
        boolean applied = applySnapshotJson(json);
        if (applied) {
            renewTtl();
        }
        return applied;
    }

    public boolean applySnapshotJson(String json) {
        RateLimitPolicySnapshot snapshot = codec.decode(json);
        DynamicPolicyApplyResult result = registry.applyDynamicRevision(
                codec.toMap(snapshot), snapshot.revision(), Instant.now());
        if (result == DynamicPolicyApplyResult.REJECTED_STALE) {
            return false;
        }
        registry.markSourceUnavailable(false);
        log.debug("已刷新限流策略动态快照 revision={} result={}", snapshot.revision(), result);
        return true;
    }

    public void onEvent(String revisionPayload) {
        if (revisionPayload == null || revisionPayload.isBlank()) {
            refreshFromRedis();
            return;
        }
        try {
            long remote = Long.parseLong(revisionPayload.trim());
            if (remote < registry.revision()) {
                return;
            }
        } catch (NumberFormatException ignored) {
            // ignore
        }
        refreshFromRedis();
    }

    private void renewTtl() {
        try {
            redisTemplate.expire(RateLimitRedisKeys.policySnapshot(keyPrefix), snapshotTtl);
            redisTemplate.expire(RateLimitRedisKeys.policyRevision(keyPrefix), snapshotTtl);
        } catch (RuntimeException ex) {
            log.debug("续期限流策略快照失败: {}", ex.getMessage());
        }
    }
}
