package cn.miniants.platform.admin.support;

import java.security.SecureRandom;
import java.util.Base64;

public final class SecretGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private SecretGenerator() {
    }

    public static String plaintext() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
