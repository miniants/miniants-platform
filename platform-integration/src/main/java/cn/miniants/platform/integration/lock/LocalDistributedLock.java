package cn.miniants.platform.integration.lock;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单进程锁。多实例部署下不互斥，只作为没有 Redis 时的兜底。
 */
public class LocalDistributedLock implements DistributedLock {

    private final ConcurrentHashMap<String, Held> locks = new ConcurrentHashMap<>();
    private final LockTokens tokens = new LockTokens();

    @Override
    public boolean tryLock(String key, Duration lease) {
        if (key == null || key.isBlank()) {
            return false;
        }
        long now = System.currentTimeMillis();
        Held mine = new Held(LockTokens.newToken(), now + DistributedLock.leaseMillis(lease));
        Held winner = locks.compute(key,
                (ignored, existing) -> existing != null && existing.expiresAt() > now ? existing : mine);
        if (winner != mine) {
            return false;
        }
        tokens.hold(key, mine.token());
        return true;
    }

    @Override
    public void unlock(String key) {
        String token = tokens.release(key);
        if (token == null) {
            return;
        }
        locks.computeIfPresent(key, (ignored, held) -> token.equals(held.token()) ? null : held);
    }

    private record Held(String token, long expiresAt) {
    }
}
