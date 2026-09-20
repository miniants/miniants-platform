package cn.miniants.platform.integration.idempotency;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单进程幂等标记。多实例部署下不互斥，只作为没有 Redis 时的兜底。
 */
public class LocalIdempotencyStore implements IdempotencyStore {

    private final ConcurrentHashMap<String, Long> expiresAt = new ConcurrentHashMap<>();

    @Override
    public boolean tryBegin(String key, Duration ttl) {
        if (key == null || key.isBlank()) {
            return false;
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return false;
        }
        long now = System.currentTimeMillis();
        long ttlMs = ttl.toMillis();
        AtomicBoolean begun = new AtomicBoolean();
        expiresAt.compute(key, (ignored, existing) -> {
            if (existing != null && existing > now) {
                return existing;
            }
            begun.set(true);
            return now + ttlMs;
        });
        return begun.get();
    }
}
