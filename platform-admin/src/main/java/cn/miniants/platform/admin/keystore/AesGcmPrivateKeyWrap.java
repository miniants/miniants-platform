package cn.miniants.platform.admin.keystore;

import cn.miniants.platform.core.error.PlatformException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.function.Function;

/**
 * JWT 私钥 AES-256-GCM 包装。密文为 Base64({@code IV || ciphertext+tag})，IV 每次随机。
 * 密钥材料不写日志、不进异常消息。
 */
public final class AesGcmPrivateKeyWrap {

    public static final String ENV_WRAP_KEY = "PLATFORM_JWT_WRAP_KEY";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Function<Boolean, String> keyBase64;

    private AesGcmPrivateKeyWrap(Function<Boolean, String> keyBase64) {
        this.keyBase64 = keyBase64;
    }

    public static AesGcmPrivateKeyWrap from(
            PlatformJwtKeystoreProperties properties, JwtWrapKeyResolver resolver) {
        if (resolver == null) {
            return from(properties);
        }
        return new AesGcmPrivateKeyWrap(resolver::resolveBase64);
    }

    public static AesGcmPrivateKeyWrap from(PlatformJwtKeystoreProperties properties) {
        String configured = properties == null ? "" : properties.getWrapKey();
        if (configured != null && !configured.isBlank()) {
            return parse(configured.trim());
        }
        return parse(System.getenv(ENV_WRAP_KEY));
    }

    public static AesGcmPrivateKeyWrap parse(String base64Key) {
        String fixed = base64Key;
        return new AesGcmPrivateKeyWrap(ensure -> fixed);
    }

    public boolean available() {
        return parseKey(keyBase64.apply(false)).key != null;
    }

    public void requireAvailable() {
        Resolved resolved = parseKey(keyBase64.apply(true));
        if (resolved.key == null) {
            throw new PlatformException(resolved.refuseMessage);
        }
    }

    public String encrypt(byte[] privateKeyBytes) {
        SecretKey secret = requireKey();
        if (privateKeyBytes == null || privateKeyBytes.length == 0) {
            throw new PlatformException("私钥材料不能为空");
        }
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secret, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(privateKeyBytes);
            byte[] packed = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(cipherText, 0, packed, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(packed);
        } catch (GeneralSecurityException ex) {
            throw new PlatformException("私钥加密失败");
        }
    }

    public byte[] decrypt(String cipherText) {
        SecretKey secret = requireKey();
        return decryptInternal(secret, cipherText)
                .orElseThrow(() -> new PlatformException("私钥密文解密失败"));
    }

    /**
     * 读路径兜底：包装密钥缺失或密文损坏时返回空，不抛、不记密钥材料。
     */
    public Optional<byte[]> tryDecrypt(String cipherText) {
        SecretKey secret = parseKey(keyBase64.apply(false)).key;
        if (secret == null) {
            return Optional.empty();
        }
        return decryptInternal(secret, cipherText);
    }

    private SecretKey requireKey() {
        Resolved resolved = parseKey(keyBase64.apply(true));
        if (resolved.key == null) {
            throw new PlatformException(resolved.refuseMessage);
        }
        return resolved.key;
    }

    static Resolved parseKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            return new Resolved(null,
                    "未配置 JWT 私钥包装密钥，无法生成或轮换。env 模式请设置 "
                            + ENV_WRAP_KEY + "；database 模式请在管理端切换到来源「本库」");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException ex) {
            return new Resolved(null, "JWT 私钥包装密钥不是合法 Base64，无法生成或轮换");
        }
        if (raw.length != KEY_BYTES) {
            return new Resolved(null, "JWT 私钥包装密钥长度必须为 256 位，无法生成或轮换");
        }
        return new Resolved(new SecretKeySpec(raw, "AES"), null);
    }

    private Optional<byte[]> decryptInternal(SecretKey secret, String cipherText) {
        if (cipherText == null || cipherText.isBlank()) {
            return Optional.empty();
        }
        byte[] packed;
        try {
            packed = Base64.getDecoder().decode(cipherText.trim());
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        if (packed.length <= IV_BYTES) {
            return Optional.empty();
        }
        byte[] iv = new byte[IV_BYTES];
        byte[] body = new byte[packed.length - IV_BYTES];
        System.arraycopy(packed, 0, iv, 0, IV_BYTES);
        System.arraycopy(packed, IV_BYTES, body, 0, body.length);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secret, new GCMParameterSpec(TAG_BITS, iv));
            return Optional.of(cipher.doFinal(body));
        } catch (GeneralSecurityException ex) {
            return Optional.empty();
        }
    }

    record Resolved(SecretKey key, String refuseMessage) {
    }
}
