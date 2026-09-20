package cn.miniants.platform.security.sas;

import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;

/**
 * 从 JKS 加载 JWT 签名密钥。
 */
public final class JwtKeyStores {

    private JwtKeyStores() {
    }

    public static KeyPair load(Resource resource, String storePassword, String alias, String keyPassword) {
        try {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            try (InputStream in = resource.getInputStream()) {
                keyStore.load(in, storePassword.toCharArray());
            }
            Key key = keyStore.getKey(alias, keyPassword.toCharArray());
            if (!(key instanceof PrivateKey privateKey)) {
                throw new IllegalStateException("JWT keystore 别名不是私钥: " + alias);
            }
            Certificate cert = keyStore.getCertificate(alias);
            if (cert == null) {
                throw new IllegalStateException("JWT keystore 缺少证书: " + alias);
            }
            return new KeyPair(cert.getPublicKey(), privateKey);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("无法加载 JWT keystore", ex);
        }
    }
}
