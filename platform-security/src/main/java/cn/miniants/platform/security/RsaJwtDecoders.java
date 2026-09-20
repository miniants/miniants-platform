package cn.miniants.platform.security;

import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资源服务器 RSA {@link JwtDecoder}：KeyPair、PEM/证书、或带 kid 的 JWK Set。
 */
public final class RsaJwtDecoders {

    private RsaJwtDecoders() {
    }

    public static JwtDecoder from(KeyPair keyPair,
                                  String publicKey,
                                  String publicKeyLocation,
                                  String jwkJson,
                                  ResourceLoader resourceLoader) {
        if (keyPair != null && keyPair.getPublic() instanceof RSAPublicKey rsaPublicKey) {
            return NimbusJwtDecoder.withPublicKey(rsaPublicKey).build();
        }
        try {
            if (StringUtils.hasText(jwkJson)) {
                return fromJwkJson(jwkJson);
            }
            if (StringUtils.hasText(publicKey)) {
                return NimbusJwtDecoder.withPublicKey(parsePublicKey(publicKey)).build();
            }
            if (StringUtils.hasText(publicKeyLocation)) {
                String content = readLocation(publicKeyLocation, resourceLoader).trim();
                return content.startsWith("{")
                        ? fromJwkJson(content)
                        : NimbusJwtDecoder.withPublicKey(parsePublicKey(content)).build();
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("无法加载 RSA JWT 公钥", e);
        }
        throw new IllegalStateException(
                "JWT 验签公钥未配置：提供 RSA KeyPair Bean，或配置 public-key / public-key-location / jwk-json");
    }

    static JwtDecoder fromJwkJson(String json) throws Exception {
        String trimmed = json.trim();
        List<JWK> keys = trimmed.contains("\"keys\"")
                ? JWKSet.parse(trimmed).getKeys()
                : List.of(JWK.parse(trimmed));
        List<RSAKey> rsaKeys = keys.stream()
                .filter(RSAKey.class::isInstance)
                .map(RSAKey.class::cast)
                .toList();
        if (rsaKeys.isEmpty()) {
            throw new IllegalArgumentException("JWK 配置中没有 RSA 公钥");
        }
        if (rsaKeys.size() == 1 && !StringUtils.hasText(rsaKeys.get(0).getKeyID())) {
            return NimbusJwtDecoder.withPublicKey(rsaKeys.get(0).toRSAPublicKey()).build();
        }
        Map<String, RSAPublicKey> keyed = new LinkedHashMap<>();
        for (RSAKey rsaKey : rsaKeys) {
            if (!StringUtils.hasText(rsaKey.getKeyID())) {
                throw new IllegalArgumentException("多密钥 JWK Set 中每个 RSA key 都必须配置唯一 kid");
            }
            if (keyed.put(rsaKey.getKeyID(), rsaKey.toRSAPublicKey()) != null) {
                throw new IllegalArgumentException("JWK Set 中存在重复 kid: " + rsaKey.getKeyID());
            }
        }
        RSAPublicKey single = rsaKeys.size() == 1 ? keyed.values().iterator().next() : null;
        JWSKeySelector<SecurityContext> selector = (header, context) -> {
            String kid = header.getKeyID();
            RSAPublicKey key;
            if (keyed.size() == 1 && single != null && !StringUtils.hasText(kid)) {
                key = single;
            } else if (!StringUtils.hasText(kid)) {
                throw new KeySourceException("JWT kid required during key rotation");
            } else {
                key = keyed.get(kid);
            }
            if (key == null) {
                throw new KeySourceException("JWT kid not found: " + kid);
            }
            return List.of(key);
        };
        ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(selector);
        processor.setJWTClaimsSetVerifier((claims, context) -> {
        });
        return new NimbusJwtDecoder(processor);
    }

    static RSAPublicKey parsePublicKey(String encoded) throws Exception {
        if (encoded.contains("-----BEGIN CERTIFICATE-----")) {
            String normalizedCertificate = encoded
                    .replace("\\n", "\n")
                    .replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replaceAll("\\s", "");
            byte[] certificateBytes = Base64.getDecoder().decode(normalizedCertificate);
            X509Certificate certificate = (X509Certificate) CertificateFactory
                    .getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(certificateBytes));
            return (RSAPublicKey) certificate.getPublicKey();
        }
        String normalized = encoded
                .replace("\\n", "\n")
                .replaceAll("-----BEGIN (RSA )?PUBLIC KEY-----", "")
                .replaceAll("-----END (RSA )?PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(normalized);
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(der));
    }

    private static String readLocation(String location, ResourceLoader resourceLoader) throws IOException {
        if (location.startsWith("env:")) {
            String variable = location.substring("env:".length()).trim();
            String value = System.getenv(variable);
            if (!StringUtils.hasText(value)) {
                throw new IllegalArgumentException("环境变量未设置: " + variable);
            }
            return value;
        }
        if (resourceLoader == null) {
            throw new IllegalArgumentException("公钥资源加载器未提供: " + location);
        }
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalArgumentException("公钥资源不存在: " + location);
        }
        try (java.io.InputStream input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
