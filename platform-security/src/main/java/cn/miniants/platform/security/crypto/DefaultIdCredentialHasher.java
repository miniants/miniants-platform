package cn.miniants.platform.security.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * SHA-256(规范化证件类型 + 规范化证号 + 可选 pepper)。规范化：trim + upper。
 */
public class DefaultIdCredentialHasher implements IdCredentialHasher {

    private final String pepper;

    public DefaultIdCredentialHasher() {
        this("");
    }

    public DefaultIdCredentialHasher(String pepper) {
        this.pepper = pepper == null ? "" : pepper;
    }

    @Override
    public String digest(String idDocType, String rawIdNumber) {
        String type = normalize(idDocType);
        String number = normalize(rawIdNumber);
        String material = type + '\0' + number + '\0' + pepper;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
