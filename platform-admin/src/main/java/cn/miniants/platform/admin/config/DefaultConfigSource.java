package cn.miniants.platform.admin.config;

import cn.miniants.platform.admin.entity.Config;
import cn.miniants.platform.admin.mapper.ConfigMapper;
import cn.miniants.platform.core.error.PlatformException;
import cn.miniants.platform.integration.cache.InvalidationBus;
import cn.miniants.platform.integration.cache.LocalTtlCache;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultConfigSource implements ConfigSource {

    public static final String REDIS_KEY_PREFIX = "platform:config:";

    private final ConfigMapper configMapper;
    private final ObjectProvider<ConfigRedisCache> redis;
    private final ObjectProvider<InvalidationBus> invalidateBus;
    private final String redisKeyPrefix;
    private final ConcurrentHashMap<String, LocalTtlCache<Optional<String>>> localCaches = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> subscribed = new ConcurrentHashMap<>();

    public DefaultConfigSource(
            ConfigMapper configMapper,
            ObjectProvider<ConfigRedisCache> redis,
            ObjectProvider<InvalidationBus> invalidateBus) {
        this(configMapper, redis, invalidateBus, REDIS_KEY_PREFIX);
    }

    public DefaultConfigSource(
            ConfigMapper configMapper,
            ObjectProvider<ConfigRedisCache> redis,
            ObjectProvider<InvalidationBus> invalidateBus,
            String redisKeyPrefix) {
        this.configMapper = configMapper;
        this.redis = redis;
        this.invalidateBus = invalidateBus;
        this.redisKeyPrefix = normalizePrefix(redisKeyPrefix);
    }

    @Override
    public Optional<String> get(String category, String key, ConfigLookup lookup) {
        ConfigLookup mode = lookup == null ? ConfigLookup.dbOnly() : lookup;
        requirePair(category, key);
        if (!mode.cache()) {
            return Optional.ofNullable(readDb(category, key));
        }
        String topic = topicOf(category, key);
        bindInvalidate(topic);
        return localCache(topic, mode).get(() -> loadShared(category, key));
    }

    @Override
    public void set(String category, String key, String value, ConfigWrite write) {
        requirePair(category, key);
        if (value == null) {
            throw new PlatformException("配置值不能为空");
        }
        ConfigLookup mode = write == null ? ConfigLookup.dbOnly() : write.lookup();
        persist(category, key, value, write == null ? null : write.title());
        String topic = topicOf(category, key);
        if (mode.cache()) {
            writeRedis(topic, value);
            bindInvalidate(topic);
            localCache(topic, mode).put(Optional.of(value));
        } else {
            deleteRedis(topic);
            invalidateLocal(topic);
        }
        publish(topic);
    }

    @Override
    public void evict(String category, String key) {
        requirePair(category, key);
        String topic = topicOf(category, key);
        deleteRedis(topic);
        invalidateLocal(topic);
        publish(topic);
    }

    public static String redisKey(String category, String key) {
        return redisKey(REDIS_KEY_PREFIX, category, key);
    }

    public static String redisKey(String prefix, String category, String key) {
        return normalizePrefix(prefix) + category.trim() + ":" + key.trim();
    }

    private String topicOf(String category, String key) {
        return redisKey(redisKeyPrefix, category, key);
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return REDIS_KEY_PREFIX;
        }
        String trimmed = prefix.trim();
        return trimmed.endsWith(":") ? trimmed : trimmed + ":";
    }

    private Optional<String> loadShared(String category, String key) {
        String topic = topicOf(category, key);
        String fromRedis = readRedis(topic);
        if (fromRedis != null) {
            return Optional.of(fromRedis);
        }
        String fromDb = readDb(category, key);
        if (fromDb != null) {
            writeRedis(topic, fromDb);
            return Optional.of(fromDb);
        }
        return Optional.empty();
    }

    private void persist(String category, String key, String value, String title) {
        Config existing = findRow(category, key);
        if (existing == null) {
            Config row = new Config();
            row.setCategory(category.trim());
            row.setConfigKey(key.trim());
            row.setConfigValue(value);
            row.setTitle(blankToNull(title));
            row.setSortNo(0);
            row.setStatus(1);
            try {
                configMapper.insert(row);
                return;
            } catch (DuplicateKeyException ex) {
                existing = findRow(category, key);
                if (existing == null) {
                    throw new PlatformException("配置键已存在");
                }
            }
        }
        existing.setConfigValue(value);
        if (title != null && !title.isBlank()) {
            existing.setTitle(title.trim());
        }
        if (configMapper.updateById(existing) == 0) {
            throw new PlatformException("配置不存在或已被修改");
        }
    }

    private Config findRow(String category, String key) {
        return configMapper.selectOne(Wrappers.<Config>lambdaQuery()
                .eq(Config::getCategory, category.trim())
                .eq(Config::getConfigKey, key.trim())
                .last("limit 1"));
    }

    private String readDb(String category, String key) {
        Config row = findRow(category, key);
        if (row == null || row.getConfigValue() == null) {
            return null;
        }
        return row.getConfigValue();
    }

    private String readRedis(String topic) {
        ConfigRedisCache cache = redis.getIfAvailable();
        return cache == null ? null : cache.get(topic);
    }

    private void writeRedis(String topic, String value) {
        ConfigRedisCache cache = redis.getIfAvailable();
        if (cache != null) {
            cache.set(topic, value);
        }
    }

    private void deleteRedis(String topic) {
        ConfigRedisCache cache = redis.getIfAvailable();
        if (cache != null) {
            cache.delete(topic);
        }
    }

    private void publish(String topic) {
        InvalidationBus bus = invalidateBus.getIfAvailable();
        if (bus != null) {
            bus.publish(topic);
        }
    }

    private void bindInvalidate(String topic) {
        if (subscribed.putIfAbsent(topic, Boolean.TRUE) != null) {
            return;
        }
        InvalidationBus bus = invalidateBus.getIfAvailable();
        if (bus == null) {
            subscribed.remove(topic);
            return;
        }
        bus.onInvalidate(topic, () -> invalidateLocal(topic));
    }

    private LocalTtlCache<Optional<String>> localCache(String topic, ConfigLookup lookup) {
        return localCaches.computeIfAbsent(topic, ignored -> new LocalTtlCache<>(lookup.localTtl().toMillis()));
    }

    private void invalidateLocal(String topic) {
        LocalTtlCache<Optional<String>> cache = localCaches.get(topic);
        if (cache != null) {
            cache.invalidate();
        }
    }

    private static void requirePair(String category, String key) {
        if (category == null || category.isBlank() || key == null || key.isBlank()) {
            throw new PlatformException("配置分类和键不能为空");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
