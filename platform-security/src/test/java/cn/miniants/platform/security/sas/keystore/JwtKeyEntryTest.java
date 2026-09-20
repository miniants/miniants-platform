package cn.miniants.platform.security.sas.keystore;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtKeyEntryTest {

    @Test
    void toStringDoesNotLeakPrivateKey() {
        KeyPair pair = rsa();
        JwtKeyEntry entry = JwtKeyEntry.signing("active", pair);
        String text = entry.toString();
        assertTrue(text.contains("kid=active"));
        assertTrue(text.contains("hasPrivateKey=true"));
        String encoded = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        assertFalse(text.contains(encoded));
        assertFalse(text.contains("PRIVATE KEY"));
    }

    @Test
    void activeRequiresPrivateKey() {
        KeyPair pair = rsa();
        assertThrows(IllegalArgumentException.class,
                () -> new JwtKeyEntry("x", pair.getPublic(), null, true, JwtKeyEntry.ALG_RS256));
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
