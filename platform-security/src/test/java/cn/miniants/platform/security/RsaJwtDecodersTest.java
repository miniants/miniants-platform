package cn.miniants.platform.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RsaJwtDecodersTest {

    @Test
    void decodesWithKeyPairAndRotatedJwkKid() throws Exception {
        KeyPair first = rsa();
        KeyPair second = rsa();
        JwtDecoder fromKeyPair = RsaJwtDecoders.from(first, null, null, null, null);
        assertEquals("alice", fromKeyPair.decode(token(first, Map.of("username", "alice"), null))
                .getClaimAsString("username"));

        RSAKey firstJwk = new RSAKey.Builder((RSAPublicKey) first.getPublic()).keyID("first").build();
        RSAKey secondJwk = new RSAKey.Builder((RSAPublicKey) second.getPublic()).keyID("second").build();
        JwtDecoder rotating = RsaJwtDecoders.from(
                null, null, null, new JWKSet(List.of(firstJwk, secondJwk)).toString(), null);
        assertEquals("kiosk", rotating.decode(token(second, Map.of("client_id", "kiosk"), "second"))
                .getClaimAsString("client_id"));
        assertThrows(JwtException.class,
                () -> rotating.decode(token(second, Map.of("client_id", "kiosk"), "unknown")));
        assertThrows(JwtException.class,
                () -> rotating.decode(token(second, Map.of("client_id", "kiosk"), null)));
    }

    private static KeyPair rsa() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String token(KeyPair signingKey, Map<String, Object> claims, String keyId) throws Exception {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .expirationTime(new Date(System.currentTimeMillis() + 60_000));
        claims.forEach(builder::claim);
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId).build();
        SignedJWT jwt = new SignedJWT(header, builder.build());
        jwt.sign(new RSASSASigner(signingKey.getPrivate()));
        return jwt.serialize();
    }
}
