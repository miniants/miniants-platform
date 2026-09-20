package cn.miniants.platform.integration.lock;

import cn.miniants.platform.core.error.PlatformCodes;
import cn.miniants.platform.core.error.PlatformException;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 互斥锁。实现之间语义必须一致，换实现不能改变行为。
 */
public interface DistributedLock {

    /** 无租约时按此时长持有，避免持有者崩溃后锁永不释放。 */
    Duration DEFAULT_LEASE = Duration.ofSeconds(30);

    /**
     * 非阻塞抢锁：拿不到立即返回 false，不等待。
     *
     * @param lease 持有时长，到期自动释放；null 或非正数按 {@link #DEFAULT_LEASE} 处理
     */
    boolean tryLock(String key, Duration lease);

    /** 释放本线程持有的锁。未持有或已被租约回收时静默返回。 */
    void unlock(String key);

    static long leaseMillis(Duration lease) {
        long millis = lease == null ? 0L : lease.toMillis();
        return millis > 0L ? millis : DEFAULT_LEASE.toMillis();
    }

    default <T> T withLock(String key, Duration lease, Supplier<T> action) {
        if (!tryLock(key, lease)) {
            throw new PlatformException(PlatformCodes.LOCK_NOT_ACQUIRED);
        }
        try {
            return action.get();
        } finally {
            unlock(key);
        }
    }
}
