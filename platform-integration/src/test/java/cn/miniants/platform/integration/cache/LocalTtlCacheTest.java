package cn.miniants.platform.integration.cache;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LocalTtlCacheTest {

    /** 命中还去调 loader 就等于每次都打 Redis，这个类就白写了。 */
    @Test
    void hitDoesNotCallLoader() {
        LocalTtlCache<String> cache = new LocalTtlCache<>(60_000L);
        AtomicInteger loads = new AtomicInteger();
        cache.put("a");
        String v = cache.get(() -> {
            loads.incrementAndGet();
            return "b";
        });
        assertEquals("a", v);
        assertEquals(0, loads.get());
    }

    @Test
    void invalidateForcesReload() {
        LocalTtlCache<String> cache = new LocalTtlCache<>(60_000L);
        AtomicInteger loads = new AtomicInteger();
        cache.put("a");
        cache.invalidate();
        assertNull(cache.peek());
        String v = cache.get(() -> {
            loads.incrementAndGet();
            return "b";
        });
        assertEquals("b", v);
        assertEquals(1, loads.get());
    }
}
