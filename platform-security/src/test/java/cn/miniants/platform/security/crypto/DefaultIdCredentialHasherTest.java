package cn.miniants.platform.security.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DefaultIdCredentialHasherTest {

    @Test
    void normalizesAndIsStable() {
        IdCredentialHasher hasher = new DefaultIdCredentialHasher("pepper");
        String a = hasher.digest("id_card", " 110101199001011234 ");
        String b = hasher.digest("ID_CARD", "110101199001011234");
        assertEquals(a, b);
        assertEquals(64, a.length());
    }

    @Test
    void pepperChangesDigest() {
        String plain = new DefaultIdCredentialHasher().digest("id_card", "X");
        String peppered = new DefaultIdCredentialHasher("p").digest("id_card", "X");
        assertNotEquals(plain, peppered);
    }
}
