package cn.miniants.platform.admin.keystore;

import cn.miniants.platform.core.error.PlatformException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmPrivateKeyWrapTest {

    @Test
    void encryptDecryptRoundTrip() {
        AesGcmPrivateKeyWrap wrap = AesGcmPrivateKeyWrap.parse(randomKey());
        byte[] plain = "pkcs8-private-key-bytes".getBytes(StandardCharsets.UTF_8);

        String first = wrap.encrypt(plain);
        String second = wrap.encrypt(plain);

        assertThat(first).isNotBlank().isNotEqualTo(second);
        assertThat(wrap.decrypt(first)).isEqualTo(plain);
        assertThat(wrap.decrypt(second)).isEqualTo(plain);
        assertThat(first).doesNotContain("pkcs8-private-key-bytes");
    }

    @Test
    void missingWrapKeyRefusesWriteButTryDecryptIsEmpty() {
        AesGcmPrivateKeyWrap missing = AesGcmPrivateKeyWrap.parse(null);

        assertThat(missing.available()).isFalse();
        assertThatThrownBy(() -> missing.encrypt(new byte[]{1}))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("未配置")
                .hasMessageContaining(AesGcmPrivateKeyWrap.ENV_WRAP_KEY);
        assertThat(missing.tryDecrypt("anything")).isEmpty();
    }

    @Test
    void invalidWrapKeyRefusesWrite() {
        AesGcmPrivateKeyWrap invalid = AesGcmPrivateKeyWrap.parse("not-base64!!");

        assertThatThrownBy(() -> invalid.encrypt(new byte[]{1}))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    void wrongLengthWrapKeyRefusesWrite() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        AesGcmPrivateKeyWrap wrap = AesGcmPrivateKeyWrap.parse(shortKey);

        assertThatThrownBy(() -> wrap.encrypt(new byte[]{1}))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("256");
    }

    static String randomKey() {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        return Base64.getEncoder().encodeToString(raw);
    }
}
