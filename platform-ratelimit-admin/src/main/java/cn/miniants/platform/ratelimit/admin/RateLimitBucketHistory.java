package cn.miniants.platform.ratelimit.admin;

import cn.miniants.platform.core.json.Jsons;
import cn.miniants.platform.ratelimit.admin.dto.RateLimitBucketsVo;
import cn.miniants.platform.ratelimit.admin.service.RateLimitPolicyAdminService;
import cn.miniants.platform.ratelimit.config.RateLimitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * 管理面可选实时历史：按间隔把 {@code /buckets} 整帧写入单个 ZSET，供进入控制台时预载。
 * 不改限流 Lua，不影响 {@code GET /buckets}。
 */
public class RateLimitBucketHistory {

    private static final Logger log = LoggerFactory.getLogger(RateLimitBucketHistory.class);

    static final String SNAPSHOTS_SUFFIX = "live:snapshots";
    static final String SAMPLE_SEGMENT = "live:sample:";

    private final StringRedisTemplate redis;
    private final RateLimitPolicyAdminService adminService;
    private final String snapshotsKey;
    private final String sampleKeyPrefix;
    private final Duration sampleInterval;
    private final Duration retention;
    private final LongSupplier clock;
    private volatile boolean failed;

    public RateLimitBucketHistory(
            StringRedisTemplate redis,
            RateLimitProperties rateLimitProperties,
            RateLimitAdminProperties adminProperties,
            RateLimitPolicyAdminService adminService) {
        this(redis, rateLimitProperties, adminProperties, adminService, System::currentTimeMillis);
    }

    RateLimitBucketHistory(
            StringRedisTemplate redis,
            RateLimitProperties rateLimitProperties,
            RateLimitAdminProperties adminProperties,
            RateLimitPolicyAdminService adminService,
            LongSupplier clock) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.adminService = Objects.requireNonNull(adminService, "adminService");
        this.clock = Objects.requireNonNull(clock, "clock");
        String prefix = requireKeyPrefix(rateLimitProperties);
        this.snapshotsKey = prefix + SNAPSHOTS_SUFFIX;
        this.sampleKeyPrefix = prefix + SAMPLE_SEGMENT;
        RateLimitAdminProperties.Live live = Objects.requireNonNull(adminProperties, "adminProperties").getLive();
        validateLive(live);
        this.sampleInterval = live.getSampleInterval();
        this.retention = live.getRetention();
        log.info("限流实时历史已启用 prefix={} interval={} retention={}", prefix, sampleInterval, retention);
    }

    static void validateLive(RateLimitAdminProperties.Live live) {
        Objects.requireNonNull(live, "live");
        Duration interval = live.getSampleInterval();
        Duration keep = live.getRetention();
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new IllegalStateException("platform.ratelimit.admin.live.sample-interval 必须为正");
        }
        if (keep == null || keep.isZero() || keep.isNegative()) {
            throw new IllegalStateException("platform.ratelimit.admin.live.retention 必须为正");
        }
        if (keep.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalStateException("platform.ratelimit.admin.live.retention 不能超过 1h");
        }
        long frames = keep.toMillis() / interval.toMillis();
        if (frames > RateLimitAdminProperties.Live.MAX_FRAMES) {
            throw new IllegalStateException(
                    "platform.ratelimit.admin.live 的 retention / sample-interval 不能超过 "
                            + RateLimitAdminProperties.Live.MAX_FRAMES);
        }
    }

    @Scheduled(fixedDelayString = "${platform.ratelimit.admin.live.sample-interval:30s}")
    public void sample() {
        try {
            long nowMs = clock.getAsLong();
            long slot = nowMs / sampleInterval.toMillis();
            Boolean acquired = redis.opsForValue().setIfAbsent(sampleKey(slot), "1", retention);
            if (!Boolean.TRUE.equals(acquired)) {
                recoverIfNeeded();
                return;
            }
            RateLimitBucketsVo snapshot = adminService.buckets(500);
            if (snapshot == null || snapshot.getBuckets() == null || snapshot.getBuckets().isEmpty()) {
                recoverIfNeeded();
                return;
            }
            Instant observedAt = snapshot.getObservedAt() == null
                    ? Instant.ofEpochMilli(nowMs)
                    : snapshot.getObservedAt();
            snapshot.setObservedAt(observedAt);
            long score = observedAt.toEpochMilli();
            redis.opsForZSet().add(snapshotsKey, Jsons.toJson(snapshot), score);
            long cutoff = nowMs - retention.toMillis();
            if (cutoff > 0L) {
                redis.opsForZSet().removeRangeByScore(snapshotsKey, 0, cutoff - 1);
            }
            redis.expire(snapshotsKey, retention);
            recoverIfNeeded();
        } catch (RuntimeException ex) {
            markFailed(ex);
        }
    }

    public List<RateLimitBucketsVo> list() {
        try {
            long cutoff = clock.getAsLong() - retention.toMillis();
            Set<String> raw = redis.opsForZSet().rangeByScore(snapshotsKey, cutoff, Double.POSITIVE_INFINITY);
            if (raw == null || raw.isEmpty()) {
                return List.of();
            }
            List<RateLimitBucketsVo> frames = new ArrayList<>();
            for (String json : raw) {
                RateLimitBucketsVo frame = readFrame(json);
                if (frame != null) {
                    frames.add(frame);
                }
            }
            if (frames.size() > RateLimitAdminProperties.Live.MAX_FRAMES) {
                return List.copyOf(frames.subList(
                        frames.size() - RateLimitAdminProperties.Live.MAX_FRAMES, frames.size()));
            }
            return List.copyOf(frames);
        } catch (RuntimeException ex) {
            markFailed(ex);
            return List.of();
        }
    }

    private static RateLimitBucketsVo readFrame(String json) {
        try {
            return Jsons.readValue(json, RateLimitBucketsVo.class);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String sampleKey(long slot) {
        return sampleKeyPrefix + slot;
    }

    private void markFailed(RuntimeException ex) {
        if (!failed) {
            failed = true;
            log.warn("限流实时历史暂不可用，已跳过本轮", ex);
        }
    }

    private void recoverIfNeeded() {
        if (failed) {
            failed = false;
            log.info("限流实时历史已恢复");
        }
    }

    private static String requireKeyPrefix(RateLimitProperties properties) {
        String prefix = Objects.requireNonNull(properties, "rateLimitProperties").getKeyPrefix();
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalStateException("platform.ratelimit.key-prefix 不能为空");
        }
        return prefix;
    }
}
