package cn.miniants.platform.security.sas.keystore;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RotatableJwkSourceTest {

    @Test
    void snapshotPublishesAllKidsAndOnlyActiveHasPrivateKey() {
        KeyPair oldPair = rsa();
        KeyPair activePair = rsa();
        InMemoryJwtKeyStore store = new InMemoryJwtKeyStore(
                JwtKeyEntry.verifyOnly("old", oldPair.getPublic()),
                JwtKeyEntry.signing("active", activePair));
        RotatableJwkSource source = new RotatableJwkSource(store);

        List<JWK> keys = allKeys(source);
        assertEquals(List.of("active", "old"), keys.stream().map(JWK::getKeyID).toList());
        assertEquals("active", source.activeKid());
        assertTrue(keys.get(0).isPrivate());
        assertFalse(keys.get(1).isPrivate());
    }

    @Test
    void encoderSignsWithActiveKidEvenWhenOlderKeysComeFirstInStore() {
        KeyPair oldPair = rsa();
        KeyPair activePair = rsa();
        InMemoryJwtKeyStore store = new InMemoryJwtKeyStore(
                JwtKeyEntry.verifyOnly("old", oldPair.getPublic()),
                JwtKeyEntry.signing("active", activePair));
        RotatableJwkSource source = new RotatableJwkSource(store);

        assertEquals("active", encodeKid(source));
    }

    @Test
    void refreshSwitchesSigningKidAfterRetireAndActivate() {
        KeyPair firstPair = rsa();
        KeyPair secondPair = rsa();
        InMemoryJwtKeyStore store = new InMemoryJwtKeyStore(
                JwtKeyEntry.signing("first", firstPair),
                JwtKeyEntry.verifyOnly("second", secondPair.getPublic()));
        RotatableJwkSource source = new RotatableJwkSource(store);
        assertEquals("first", encodeKid(source));
        assertEquals(List.of("first", "second"), allKeys(source).stream().map(JWK::getKeyID).toList());

        store.replace(
                JwtKeyEntry.verifyOnly("first", firstPair.getPublic()),
                JwtKeyEntry.signing("second", secondPair));
        source.reload();

        assertEquals("second", encodeKid(source));
        List<JWK> keys = allKeys(source);
        assertEquals(List.of("second", "first"), keys.stream().map(JWK::getKeyID).toList());
        assertTrue(keys.get(0).isPrivate());
        assertFalse(keys.get(1).isPrivate());
    }

    @Test
    void emptyPrimaryStoreFallsBackToClasspathStore() {
        KeyPair fallbackPair = rsa();
        JwtKeyStore empty = new InMemoryJwtKeyStore();
        ClasspathSingleKeyStore fallback = new ClasspathSingleKeyStore(fallbackPair, "jwt");
        RotatableJwkSource source = new RotatableJwkSource(empty, fallback);

        assertEquals("jwt", source.activeKid());
        assertEquals("jwt", encodeKid(source));
        assertEquals(List.of("jwt"), allKeys(source).stream().map(JWK::getKeyID).toList());
    }

    @Test
    void emptyStoreWithoutFallbackFails() {
        assertThrows(IllegalStateException.class, () -> new RotatableJwkSource(new InMemoryJwtKeyStore()));
    }

    @Test
    void selectSigningJwkPicksTheOnlyPrivateKey() {
        KeyPair first = rsa();
        KeyPair second = rsa();
        RotatableJwkSource source = new RotatableJwkSource(new InMemoryJwtKeyStore(
                JwtKeyEntry.verifyOnly("old", first.getPublic()),
                JwtKeyEntry.signing("active", second)));
        JWK selected = RotatableJwkSource.selectSigningJwk(allKeys(source));
        assertEquals("active", selected.getKeyID());
        assertTrue(selected.isPrivate());
    }

    private static String encodeKid(RotatableJwkSource source) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(source);
        encoder.setJwkSelector(RotatableJwkSource::selectSigningJwk);
        Instant now = Instant.now();
        Jwt jwt = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(),
                JwtClaimsSet.builder()
                        .subject("alice")
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(60))
                        .build()));
        Object kid = jwt.getHeaders().get("kid");
        return kid == null ? null : kid.toString();
    }

    private static List<JWK> allKeys(RotatableJwkSource source) {
        return source.get(new JWKSelector(new JWKMatcher.Builder().build()), null);
    }

    private static KeyPair rsa() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
