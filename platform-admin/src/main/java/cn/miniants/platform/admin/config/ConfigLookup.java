package cn.miniants.platform.admin.config;

import java.time.Duration;

/**
 * 单次读写是否走缓存。DB 始终是真源；{@link #cached()} 才读 Redis / 本地 TTL。
 */
public final class ConfigLookup {

    private static final Duration DEFAULT_LOCAL_TTL = Duration.ofSeconds(30);

    private final boolean cache;
    private final Duration localTtl;

    private ConfigLookup(boolean cache, Duration localTtl) {
        this.cache = cache;
        this.localTtl = localTtl;
    }

    public static ConfigLookup dbOnly() {
        return new ConfigLookup(false, DEFAULT_LOCAL_TTL);
    }

    public static ConfigLookup cached() {
        return cached(DEFAULT_LOCAL_TTL);
    }

    public static ConfigLookup cached(Duration localTtl) {
        if (localTtl == null || localTtl.isZero() || localTtl.isNegative()) {
            throw new IllegalArgumentException("cached 的本地 TTL 必须为正");
        }
        return new ConfigLookup(true, localTtl);
    }

    public boolean cache() {
        return cache;
    }

    public Duration localTtl() {
        return localTtl;
    }
}
