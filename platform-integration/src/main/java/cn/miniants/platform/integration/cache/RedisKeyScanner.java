package cn.miniants.platform.integration.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 用 SCAN 列键，不要 {@code RedisTemplate.keys}——后者在大库上会阻塞整个实例。
 */
public final class RedisKeyScanner {

    private static final Logger log = LoggerFactory.getLogger(RedisKeyScanner.class);

    private RedisKeyScanner() {
    }

    public static Set<String> scan(RedisTemplate<?, ?> redisTemplate, String pattern) {
        Set<String> keys = new LinkedHashSet<>();
        if (redisTemplate == null || pattern == null || pattern.isBlank()) {
            return keys;
        }
        RedisConnectionFactory factory = redisTemplate.getConnectionFactory();
        if (factory == null) {
            return keys;
        }
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(256).build();
        RedisConnection connection;
        try {
            connection = factory.getConnection();
        } catch (RuntimeException ex) {
            log.warn("获取 Redis SCAN 连接失败 pattern={}", pattern, ex);
            return keys;
        }
        try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
            while (cursor.hasNext()) {
                keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
            }
        } catch (Exception ex) {
            log.warn("Redis SCAN 失败 pattern={}", pattern, ex);
            return keys;
        } finally {
            try {
                connection.close();
            } catch (RuntimeException ex) {
                log.warn("关闭 Redis SCAN 连接失败 pattern={}", pattern, ex);
            }
        }
        return keys;
    }
}
