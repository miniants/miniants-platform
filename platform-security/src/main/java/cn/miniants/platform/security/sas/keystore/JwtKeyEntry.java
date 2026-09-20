package cn.miniants.platform.security.sas.keystore;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * 一把签发或只验签密钥。{@link #toString()} 不输出私钥材料。
 */
public final class JwtKeyEntry {

    public static final String ALG_RS256 = "RS256";

    private final String kid;
    private final PublicKey publicKey;
    private final PrivateKey privateKey;
    private final boolean active;
    private final String algorithm;

    public JwtKeyEntry(String kid, PublicKey publicKey, PrivateKey privateKey, boolean active, String algorithm) {
        if (kid == null || kid.isBlank()) {
            throw new IllegalArgumentException("JWT kid 不能为空");
        }
        if (publicKey == null) {
            throw new IllegalArgumentException("JWT 公钥不能为空: " + kid);
        }
        if (active && privateKey == null) {
            throw new IllegalArgumentException("active 签发钥必须带私钥: " + kid);
        }
        this.kid = kid;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.active = active;
        this.algorithm = algorithm == null || algorithm.isBlank() ? ALG_RS256 : algorithm;
    }

    public static JwtKeyEntry signing(String kid, KeyPair keyPair) {
        return signing(kid, keyPair, ALG_RS256);
    }

    public static JwtKeyEntry signing(String kid, KeyPair keyPair, String algorithm) {
        if (keyPair == null) {
            throw new IllegalArgumentException("JWT KeyPair 不能为空: " + kid);
        }
        return new JwtKeyEntry(kid, keyPair.getPublic(), keyPair.getPrivate(), true, algorithm);
    }

    public static JwtKeyEntry verifyOnly(String kid, PublicKey publicKey) {
        return verifyOnly(kid, publicKey, ALG_RS256);
    }

    public static JwtKeyEntry verifyOnly(String kid, PublicKey publicKey, String algorithm) {
        return new JwtKeyEntry(kid, publicKey, null, false, algorithm);
    }

    public String kid() {
        return kid;
    }

    public PublicKey publicKey() {
        return publicKey;
    }

    /**
     * 签发钥才有私钥；只验签条目为 {@code null}。
     */
    public PrivateKey privateKey() {
        return privateKey;
    }

    public boolean hasPrivateKey() {
        return privateKey != null;
    }

    public boolean active() {
        return active;
    }

    public String algorithm() {
        return algorithm;
    }

    public RSAPublicKey rsaPublicKey() {
        if (publicKey instanceof RSAPublicKey rsa) {
            return rsa;
        }
        throw new IllegalStateException("JWT 公钥不是 RSA: " + kid);
    }

    public RSAPrivateKey rsaPrivateKey() {
        if (privateKey instanceof RSAPrivateKey rsa) {
            return rsa;
        }
        throw new IllegalStateException("JWT 私钥不是 RSA: " + kid);
    }

    @Override
    public String toString() {
        return "JwtKeyEntry[kid=" + kid
                + ", active=" + active
                + ", algorithm=" + algorithm
                + ", hasPrivateKey=" + hasPrivateKey()
                + "]";
    }
}
