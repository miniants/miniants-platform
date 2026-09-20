package cn.miniants.platform.security.qr;

import cn.miniants.platform.core.error.PlatformException;
import cn.miniants.platform.core.json.Jsons;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis 扫码会话。键前缀可配。
 * 兼容旧字面量 INIT / SCANNED / {@code openid,username,wxCode} 与 JSON。
 */
public class RedisQrLoginStore implements QrLoginStore {

    private static final Duration TTL_INIT = Duration.ofHours(24);
    private static final Duration TTL_SCANNED = Duration.ofMinutes(5);
    private static final Duration TTL_SUCCESS = Duration.ofSeconds(10);

    private final StringRedisTemplate redis;
    private final String keyPrefix;

    public RedisQrLoginStore(StringRedisTemplate redis, String keyPrefix) {
        this.redis = redis;
        this.keyPrefix = keyPrefix == null || keyPrefix.isBlank() ? "platform:auth:qr:" : keyPrefix;
    }

    @Override
    public QrLoginSession create(String initiatingClientId) {
        if (initiatingClientId == null || initiatingClientId.isBlank()) {
            throw new PlatformException("扫码发起端未配置");
        }
        String scene = UUID.randomUUID().toString().replace("-", "");
        Stored state = new Stored();
        state.status = QrLoginStatus.INIT.name();
        state.clientId = initiatingClientId.trim();
        write(scene, state, TTL_INIT);
        return toSession(scene, state);
    }

    @Override
    public Optional<QrLoginSession> find(String scene) {
        if (scene == null || scene.isBlank()) {
            return Optional.empty();
        }
        String raw = redis.opsForValue().get(key(scene.trim()));
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(toSession(scene.trim(), decode(raw)));
    }

    @Override
    public QrLoginSession markScanned(String scene, String provider, String subject) {
        Stored state = require(scene);
        if (!QrLoginStatus.INIT.name().equals(state.status)
                && !QrLoginStatus.SCANNED.name().equals(state.status)) {
            throw new PlatformException("二维码状态无效");
        }
        state.status = QrLoginStatus.SCANNED.name();
        state.provider = blankToNull(provider);
        state.openId = blankToNull(subject);
        write(scene, state, TTL_SCANNED);
        return toSession(scene, state);
    }

    @Override
    public QrLoginSession markSuccess(String scene, String provider, String subject, String designatedUsername) {
        Stored state = require(scene);
        if (!QrLoginStatus.SCANNED.name().equals(state.status)
                && !QrLoginStatus.SUCCESS.name().equals(state.status)) {
            throw new PlatformException("请先扫码");
        }
        if (blankToNull(provider) != null) {
            state.provider = provider.trim();
        }
        if (blankToNull(subject) != null) {
            state.openId = subject.trim();
        }
        if (state.openId == null || state.openId.isBlank()) {
            throw new PlatformException("扫码身份无效");
        }
        state.status = QrLoginStatus.SUCCESS.name();
        state.designatedUsername = blankToNull(designatedUsername);
        write(scene, state, TTL_SUCCESS);
        return toSession(scene, state);
    }

    @Override
    public void remove(String scene) {
        if (scene == null || scene.isBlank()) {
            return;
        }
        redis.delete(key(scene.trim()));
    }

    private Stored require(String scene) {
        return find(scene).map(s -> {
            Stored st = new Stored();
            st.status = s.status().name();
            st.clientId = s.initiatingClientId();
            st.provider = s.provider();
            st.openId = s.subject();
            st.designatedUsername = s.designatedUsername();
            return st;
        }).orElseThrow(() -> new PlatformException("二维码已失效"));
    }

    private void write(String scene, Stored state, Duration ttl) {
        redis.opsForValue().set(key(scene), Jsons.toJson(state), ttl);
    }

    private String key(String scene) {
        return keyPrefix + scene;
    }

    static Stored decode(String raw) {
        String value = raw.trim();
        if (value.startsWith("{")) {
            Stored parsed = Jsons.readValue(value, Stored.class);
            return parsed == null ? new Stored() : parsed;
        }
        Stored legacy = new Stored();
        if (value.startsWith(QrLoginStatus.INIT.name())) {
            legacy.status = QrLoginStatus.INIT.name();
            return legacy;
        }
        if (value.startsWith(QrLoginStatus.SCANNED.name())) {
            legacy.status = QrLoginStatus.SCANNED.name();
            return legacy;
        }
        String[] parts = value.split(",", -1);
        legacy.status = QrLoginStatus.SUCCESS.name();
        legacy.openId = nullToken(parts, 0);
        legacy.designatedUsername = nullToken(parts, 1);
        legacy.wxCode = nullToken(parts, 2);
        return legacy;
    }

    private static QrLoginSession toSession(String scene, Stored state) {
        QrLoginStatus status;
        try {
            status = QrLoginStatus.valueOf(state.status == null ? QrLoginStatus.INIT.name() : state.status);
        } catch (RuntimeException ex) {
            status = QrLoginStatus.INIT;
        }
        return new QrLoginSession(
                scene,
                status,
                state.clientId,
                state.provider,
                state.openId,
                state.designatedUsername);
    }

    private static String nullToken(String[] parts, int index) {
        if (parts.length <= index) {
            return "";
        }
        String token = parts[index];
        return "null".equals(token) ? "" : token;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Stored {
        public String status;
        public String clientId;
        public String provider;
        public String openId;
        public String designatedUsername;
        public String wxCode;
    }
}
