package cn.miniants.platform.admin.keystore;

import cn.miniants.platform.core.error.PlatformException;
import cn.miniants.platform.core.json.Jsons;
import tools.jackson.databind.JsonNode;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 公钥 PEM / 精简 JWK JSON 编解码，以及公钥指纹。不处理私钥明文。
 */
public final class JwtPemKeys {

    private static final String PUBLIC_BEGIN = "-----BEGIN PUBLIC KEY-----";
    private static final String PUBLIC_END = "-----END PUBLIC KEY-----";

    private JwtPemKeys() {
    }

    public static String encodePublicPem(PublicKey publicKey) {
        if (publicKey == null) {
            throw new PlatformException("公钥不能为空");
        }
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(publicKey.getEncoded());
        return PUBLIC_BEGIN + "\n" + body + "\n" + PUBLIC_END + "\n";
    }

    public static PublicKey decodePublic(String pemOrJwk) {
        if (pemOrJwk == null || pemOrJwk.isBlank()) {
            throw new PlatformException("公钥不能为空");
        }
        String trimmed = pemOrJwk.trim();
        try {
            if (trimmed.startsWith("{")) {
                return decodeJwkPublic(trimmed);
            }
            return decodePublicPem(trimmed);
        } catch (PlatformException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformException("公钥格式无法解析");
        }
    }

    public static PrivateKey decodePrivatePkcs8(byte[] pkcs8) {
        if (pkcs8 == null || pkcs8.length == 0) {
            throw new PlatformException("私钥材料不能为空");
        }
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (GeneralSecurityException ex) {
            throw new PlatformException("私钥材料无法解析");
        }
    }

    /**
     * 公钥 DER 的 SHA-256 小写十六进制指纹，不含私钥材料。
     */
    public static String fingerprint(PublicKey publicKey) {
        if (publicKey == null) {
            return "";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded());
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException ex) {
            throw new PlatformException("无法计算公钥指纹");
        }
    }

    private static PublicKey decodePublicPem(String pem) throws GeneralSecurityException {
        String body = pem.replace(PUBLIC_BEGIN, "").replace(PUBLIC_END, "").replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(body);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    private static PublicKey decodeJwkPublic(String json) throws Exception {
        JsonNode node = Jsons.mapper().readTree(json);
        JsonNode n = node.get("n");
        JsonNode e = node.get("e");
        if (n == null || e == null || n.asString() == null || e.asString() == null) {
            throw new PlatformException("JWK 公钥缺少 n/e");
        }
        byte[] modulus = Base64.getUrlDecoder().decode(n.asString());
        byte[] exponent = Base64.getUrlDecoder().decode(e.asString());
        RSAPublicKeySpec spec = new RSAPublicKeySpec(new BigInteger(1, modulus), new BigInteger(1, exponent));
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }
}
