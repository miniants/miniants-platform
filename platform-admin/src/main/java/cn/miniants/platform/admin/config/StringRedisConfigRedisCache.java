package cn.miniants.platform.admin.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

public final class StringRedisConfigRedisCache implements ConfigRedisCache {

    private static final Logger log = LoggerFactory.getLogger(StringRedisConfigRedisCache.class);

    private final StringRedisTemplate redis;

    public StringRedisConfigRedisCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public String get(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (RuntimeException e) {
            log.debug("读取配置 Redis 失败 key={}: {}", key, e.getMessage());
            return null;
        }
    }

    @Override
    public void set(String key, String value) {
        try {
            redis.opsForValue().set(key, value);
        } catch (RuntimeException e) {
            log.warn("写入配置 Redis 失败 key={}: {}", key, e.getMessage());
        }
    }

    @Override
    public void delete(String key) {
        try {
            redis.delete(key);
        } catch (RuntimeException e) {
            log.debug("删除配置 Redis 失败 key={}: {}", key, e.getMessage());
        }
    }
}
