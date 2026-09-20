package cn.miniants.platform.security.client;

import cn.miniants.platform.core.json.Jsons;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * OAuth 客户端 Redis 缓存。值 JSON；键前缀默认 {@code platform:oauth:client:}。
 */
public final class RedisOauthClientCache implements OauthClientCache {

    private static final Logger log = LoggerFactory.getLogger(RedisOauthClientCache.class);

    private final StringRedisTemplate redis;
    private final String keyPrefix;
    private final Duration ttl;

    public RedisOauthClientCache(StringRedisTemplate redis, String keyPrefix, Duration ttl) {
        this.redis = redis;
        this.keyPrefix = normalizePrefix(keyPrefix);
        this.ttl = ttl == null || ttl.isZero() || ttl.isNegative() ? Duration.ofMinutes(10) : ttl;
    }

    @Override
    public Optional<OauthClientDescriptor> get(String clientId) {
        if (!StringUtils.hasText(clientId)) {
            return Optional.empty();
        }
        try {
            String raw = redis.opsForValue().get(key(clientId.trim()));
            if (!StringUtils.hasText(raw)) {
                return Optional.empty();
            }
            Stored stored = Jsons.readValue(raw.trim(), Stored.class);
            return Optional.ofNullable(toDescriptor(stored));
        } catch (RuntimeException ex) {
            log.warn("OAuth client 缓存读取失败: {}", clientId, ex);
            return Optional.empty();
        }
    }

    @Override
    public void put(String clientId, OauthClientDescriptor descriptor) {
        if (!StringUtils.hasText(clientId) || descriptor == null) {
            return;
        }
        try {
            redis.opsForValue().set(key(clientId.trim()), Jsons.toJson(fromDescriptor(descriptor)), ttl);
        } catch (RuntimeException ex) {
            log.warn("OAuth client 缓存写入失败: {}", clientId, ex);
        }
    }

    @Override
    public void evict(String clientId) {
        if (!StringUtils.hasText(clientId)) {
            return;
        }
        try {
            redis.delete(key(clientId.trim()));
        } catch (RuntimeException ex) {
            log.warn("OAuth client 缓存删除失败: {}", clientId, ex);
        }
    }

    private String key(String clientId) {
        return keyPrefix + clientId;
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "platform:oauth:client:";
        }
        return prefix.endsWith(":") ? prefix : prefix + ":";
    }

    private static Stored fromDescriptor(OauthClientDescriptor descriptor) {
        Stored stored = new Stored();
        stored.id = descriptor.id();
        stored.clientId = descriptor.clientId();
        stored.clientSecret = descriptor.clientSecret();
        stored.clientName = descriptor.clientName();
        stored.grantTypes = String.join(",", descriptor.grantTypes());
        stored.scopes = String.join(",", descriptor.scopes());
        stored.accessTokenTtl = descriptor.accessTokenTtlSeconds();
        stored.refreshTokenTtl = descriptor.refreshTokenTtlSeconds();
        stored.enabled = descriptor.enabled();
        return stored;
    }

    private static OauthClientDescriptor toDescriptor(Stored stored) {
        if (stored == null || !StringUtils.hasText(stored.clientId)) {
            return null;
        }
        return new OauthClientDescriptor(
                StringUtils.hasText(stored.id) ? stored.id : stored.clientId,
                stored.clientId,
                stored.clientSecret,
                StringUtils.hasText(stored.clientName) ? stored.clientName : stored.clientId,
                split(stored.grantTypes),
                split(stored.scopes),
                stored.accessTokenTtl == null ? 0 : stored.accessTokenTtl,
                stored.refreshTokenTtl == null ? 0 : stored.refreshTokenTtl,
                stored.enabled == null || stored.enabled);
    }

    private static List<String> split(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class Stored {
        public String id;
        public String clientId;
        public String clientSecret;
        public String clientName;
        public String grantTypes;
        public String scopes;
        public Integer accessTokenTtl;
        public Integer refreshTokenTtl;
        public Boolean enabled;
    }
}
