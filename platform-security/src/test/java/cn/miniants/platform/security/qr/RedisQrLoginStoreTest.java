package cn.miniants.platform.security.qr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisQrLoginStoreTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> values;

    private final Map<String, String> store = new HashMap<>();
    private RedisQrLoginStore qrLoginStore;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(invocation -> store.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            store.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        qrLoginStore = new RedisQrLoginStore(redis, "jwy:auth:qr:");
    }

    @Test
    void scannedDoesNotNeedSubjectThenAuthPassWritesOpenId() {
        QrLoginSession created = qrLoginStore.create("JWY_WEB");
        QrLoginSession scanned = qrLoginStore.markScanned(created.scene(), null, null);
        assertThat(scanned.status()).isEqualTo(QrLoginStatus.SCANNED);
        assertThat(scanned.subject()).isNull();

        QrLoginSession success = qrLoginStore.markSuccess(
                created.scene(), "JWY_MINIAPP", "oid-1", "stu001");
        assertThat(success.status()).isEqualTo(QrLoginStatus.SUCCESS);
        assertThat(success.provider()).isEqualTo("JWY_MINIAPP");
        assertThat(success.subject()).isEqualTo("oid-1");
        assertThat(success.designatedUsername()).isEqualTo("stu001");
        assertThat(success.initiatingClientId()).isEqualTo("JWY_WEB");
    }
}
