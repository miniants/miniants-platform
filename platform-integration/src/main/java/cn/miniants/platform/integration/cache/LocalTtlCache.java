package cn.miniants.platform.integration.cache;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 进程内短 TTL 缓存。命中且未过期时<strong>不</strong>调用 {@code loader}（因此也不会打 Redis）。
 * 失效靠 {@link #invalidate()}（通常由 {@link InvalidationBus}）或 TTL 到期。
 */
public final class LocalTtlCache<T> {

    private final long ttlMs;
    private final AtomicReference<Entry<T>> ref = new AtomicReference<>();

    public LocalTtlCache(long ttlMs) {
        if (ttlMs <= 0) {
            throw new IllegalArgumentException("ttlMs must be > 0");
        }
        this.ttlMs = ttlMs;
    }

    /**
     * 未过期则返回缓存值且不调用 loader；否则调用 loader 并写入。
     */
    public T get(Supplier<T> loader) {
        Entry<T> current = ref.get();
        long now = System.currentTimeMillis();
        if (current != null && now - current.loadedAt < ttlMs) {
            return current.value;
        }
        T loaded = loader.get();
        ref.set(new Entry<>(loaded, now));
        return loaded;
    }

    /** 窥视当前值（不论是否过期）；无缓存则 null。 */
    public T peek() {
        Entry<T> current = ref.get();
        return current == null ? null : current.value;
    }

    public void put(T value) {
        ref.set(new Entry<>(value, System.currentTimeMillis()));
    }

    public void invalidate() {
        ref.set(null);
    }

    private record Entry<T>(T value, long loadedAt) {
    }
}
