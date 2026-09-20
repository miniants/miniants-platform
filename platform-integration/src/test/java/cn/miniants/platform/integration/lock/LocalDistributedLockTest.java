package cn.miniants.platform.integration.lock;

import cn.miniants.platform.core.error.PlatformException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class LocalDistributedLockTest {

    @Test
    void withLockRunsExclusive() throws Exception {
        LocalDistributedLock lock = new LocalDistributedLock();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean second = new AtomicBoolean(true);

        Thread holder = new Thread(() -> lock.withLock("k", Duration.ofSeconds(1), () -> {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
        holder.start();
        entered.await();
        second.set(lock.tryLock("k", Duration.ZERO));
        release.countDown();
        holder.join();

        assertFalse(second.get());
        assertEquals("ok", lock.withLock("k", Duration.ofMillis(200), () -> "ok"));
    }

    @Test
    void withLockFailsWhenBusy() throws Exception {
        LocalDistributedLock lock = new LocalDistributedLock();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> lock.withLock("k", Duration.ofSeconds(1), () -> {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
        holder.start();
        entered.await();
        PlatformException ex = assertThrows(PlatformException.class,
                () -> lock.withLock("k", Duration.ZERO, () -> "x"));
        assertEquals("获取锁失败", ex.getMessage());
        release.countDown();
        holder.join();
        assertTrue(lock.tryLock("k", Duration.ZERO));
        lock.unlock("k");
    }

    @Test
    void leaseExpiryReleasesALockWhoseHolderNeverCameBack() throws Exception {
        LocalDistributedLock lock = new LocalDistributedLock();
        Thread abandoner = new Thread(() -> assertTrue(lock.tryLock("k", Duration.ofMillis(50))));
        abandoner.start();
        abandoner.join();

        assertFalse(lock.tryLock("k", Duration.ofSeconds(1)));
        Thread.sleep(80);
        assertTrue(lock.tryLock("k", Duration.ofSeconds(1)));
    }

    @Test
    void unlockFromAThreadThatDoesNotHoldItLeavesTheLockAlone() throws Exception {
        LocalDistributedLock lock = new LocalDistributedLock();
        CountDownLatch acquired = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            assertTrue(lock.tryLock("k", Duration.ofSeconds(5)));
            acquired.countDown();
        });
        holder.start();
        acquired.await();
        holder.join();

        lock.unlock("k");

        assertFalse(lock.tryLock("k", Duration.ofSeconds(1)));
    }

    /** lease 是持有时长不是等待时长：抢不到要立刻回来，不能等满一个租约。 */
    @Test
    void tryLockReturnsAtOnceInsteadOfWaitingOutTheLease() throws Exception {
        LocalDistributedLock lock = new LocalDistributedLock();
        CountDownLatch acquired = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            assertTrue(lock.tryLock("k", Duration.ofMinutes(5)));
            acquired.countDown();
        });
        holder.start();
        acquired.await();
        holder.join();

        long startedAt = System.nanoTime();
        assertFalse(lock.tryLock("k", Duration.ofMinutes(5)));
        assertTrue(Duration.ofNanos(System.nanoTime() - startedAt).toMillis() < 500);
    }
}
