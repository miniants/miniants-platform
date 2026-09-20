package cn.miniants.platform.admin.config;

import cn.miniants.platform.admin.entity.Config;
import cn.miniants.platform.admin.mapper.ConfigMapper;
import cn.miniants.platform.integration.cache.InvalidationBus;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultConfigSourceTest {

    @Test
    void dbOnlyDoesNotTouchRedis() {
        ConfigMapper mapper = mock(ConfigMapper.class);
        Config row = new Config();
        row.setConfigValue("plain");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);

        ConfigRedisCache redis = mock(ConfigRedisCache.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ConfigRedisCache> redisProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        @SuppressWarnings("unchecked")
        ObjectProvider<InvalidationBus> busProvider = mock(ObjectProvider.class);
        when(busProvider.getIfAvailable()).thenReturn(null);

        DefaultConfigSource source = new DefaultConfigSource(mapper, redisProvider, busProvider);
        assertEquals(Optional.of("plain"), source.get("demo.cat", "k", ConfigLookup.dbOnly()));
        verify(redis, never()).get(any());
    }

    @Test
    void cachedMissLoadsDbAndFillsRedis() {
        ConfigMapper mapper = mock(ConfigMapper.class);
        Config row = new Config();
        row.setConfigValue("from-db");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(row);

        ConfigRedisCache redis = mock(ConfigRedisCache.class);
        when(redis.get(DefaultConfigSource.redisKey("demo.cat", "k"))).thenReturn(null);

        @SuppressWarnings("unchecked")
        ObjectProvider<ConfigRedisCache> redisProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        InvalidationBus bus = mock(InvalidationBus.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<InvalidationBus> busProvider = mock(ObjectProvider.class);
        when(busProvider.getIfAvailable()).thenReturn(bus);

        DefaultConfigSource source = new DefaultConfigSource(mapper, redisProvider, busProvider);
        assertEquals(Optional.of("from-db"), source.get("demo.cat", "k", ConfigLookup.cached()));
        verify(redis).set(DefaultConfigSource.redisKey("demo.cat", "k"), "from-db");
        verify(bus).onInvalidate(any(), any());
    }

    @Test
    void setWithCacheWritesRedisAndPublishes() {
        ConfigMapper mapper = mock(ConfigMapper.class);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(mapper.insert(any(Config.class))).thenReturn(1);

        ConfigRedisCache redis = mock(ConfigRedisCache.class);

        @SuppressWarnings("unchecked")
        ObjectProvider<ConfigRedisCache> redisProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        InvalidationBus bus = mock(InvalidationBus.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<InvalidationBus> busProvider = mock(ObjectProvider.class);
        when(busProvider.getIfAvailable()).thenReturn(bus);

        DefaultConfigSource source = new DefaultConfigSource(mapper, redisProvider, busProvider);
        source.set("demo.cat", "k", "v", ConfigWrite.of("标题", ConfigLookup.cached()));

        verify(redis).set(DefaultConfigSource.redisKey("demo.cat", "k"), "v");
        verify(bus).publish(DefaultConfigSource.redisKey("demo.cat", "k"));
        assertTrue(source.get("demo.cat", "k", ConfigLookup.cached()).orElseThrow().equals("v"));
    }

    @Test
    void customPrefixWritesThatKey() {
        ConfigMapper mapper = mock(ConfigMapper.class);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(mapper.insert(any(Config.class))).thenReturn(1);

        ConfigRedisCache redis = mock(ConfigRedisCache.class);

        @SuppressWarnings("unchecked")
        ObjectProvider<ConfigRedisCache> redisProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        @SuppressWarnings("unchecked")
        ObjectProvider<InvalidationBus> busProvider = mock(ObjectProvider.class);
        when(busProvider.getIfAvailable()).thenReturn(null);

        DefaultConfigSource source = new DefaultConfigSource(mapper, redisProvider, busProvider, "app:platform:config:");
        source.set("demo.cat", "k", "v", ConfigWrite.of("标题", ConfigLookup.cached()));
        verify(redis).set("app:platform:config:demo.cat:k", "v");
    }
}
