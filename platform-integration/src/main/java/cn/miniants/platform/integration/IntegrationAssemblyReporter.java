package cn.miniants.platform.integration;

import cn.miniants.platform.integration.cache.InvalidationBus;
import cn.miniants.platform.integration.idempotency.IdempotencyStore;
import cn.miniants.platform.integration.lock.DistributedLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * 启动时记录锁 / 幂等 / 失效总线的实际装配，便于多实例上线前核对。
 */
public class IntegrationAssemblyReporter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IntegrationAssemblyReporter.class);

    private final DistributedLock lock;
    private final IdempotencyStore idempotencyStore;
    private final InvalidationBus invalidationBus;

    public IntegrationAssemblyReporter(
            DistributedLock lock,
            IdempotencyStore idempotencyStore,
            InvalidationBus invalidationBus) {
        this.lock = lock;
        this.idempotencyStore = idempotencyStore;
        this.invalidationBus = invalidationBus;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.warn("[pl] integration 装配: lock={}, idempotency={}, invalidation={}",
                lock.getClass().getSimpleName(),
                idempotencyStore.getClass().getSimpleName(),
                invalidationBus.getClass().getSimpleName());
    }
}
