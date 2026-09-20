package cn.miniants.platform.ratelimit.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * 主体哈希：SHA-256 十六进制截断 32 字符。计数键与拒绝日志已改明文，本类仅保留给兼容测试。
 */
public final class RateLimitSubjectHasher {

    private static final int HEX_LENGTH = 32;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private RateLimitSubjectHasher() {
    }

    public static String hash(String subject) {
        Objects.requireNonNull(subject, "subject");
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
        byte[] bytes = digest.digest(subject.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(HEX_LENGTH);
        for (int i = 0; i < bytes.length && sb.length() < HEX_LENGTH; i++) {
            int v = bytes[i] & 0xff;
            sb.append(HEX[v >>> 4]);
            if (sb.length() >= HEX_LENGTH) {
                break;
            }
            sb.append(HEX[v & 0x0f]);
        }
        return sb.substring(0, HEX_LENGTH);
    }
}
