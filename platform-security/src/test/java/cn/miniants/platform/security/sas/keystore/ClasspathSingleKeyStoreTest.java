package cn.miniants.platform.security.sas.keystore;

import cn.miniants.platform.security.sas.PlatformSasProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClasspathSingleKeyStoreTest {

    @Test
    void wrapsKeyPairAsSingleActiveEntry() {
        KeyPair pair = rsa();
        ClasspathSingleKeyStore store = new ClasspathSingleKeyStore(pair, "jwt");
        assertEquals(1, store.list().size());
        assertEquals("jwt", store.loadActive().kid());
        assertTrue(store.loadActive().active());
        assertTrue(store.loadActive().hasPrivateKey());
    }

    @Test
    void unconfiguredKeystoreIsEmpty() {
        PlatformSasProperties properties = new PlatformSasProperties();
        ClasspathSingleKeyStore store = new ClasspathSingleKeyStore(properties, new DefaultResourceLoader());
        assertTrue(store.list().isEmpty());
        assertNull(store.loadActive());
    }

    @Test
    void loadsConfiguredJksAsActiveAlias(@TempDir Path tempDir) throws Exception {
        Path jks = tempDir.resolve("jwt.jks");
        generateJks(jks, "jwt", "changeit", "changeit");

        PlatformSasProperties properties = new PlatformSasProperties();
        properties.getKeystore().setLocation(jks.toUri().toString());
        properties.getKeystore().setAlias("jwt");
        properties.getKeystore().setStorePassword("changeit");
        properties.getKeystore().setKeyPassword("changeit");

        ClasspathSingleKeyStore store = new ClasspathSingleKeyStore(properties, new DefaultResourceLoader());
        assertEquals("jwt", store.loadActive().kid());
        assertTrue(store.loadActive().hasPrivateKey());

        RotatableJwkSource source = new RotatableJwkSource(store);
        assertEquals("jwt", source.activeKid());
        assertEquals(1, source.currentJwkSet().getKeys().size());
    }

    private static void generateJks(Path jks, String alias, String storePassword, String keyPassword)
            throws Exception {
        String keytool = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase().contains("win") ? "keytool.exe" : "keytool")
                .toString();
        Process process = new ProcessBuilder(
                keytool,
                "-genkeypair",
                "-alias", alias,
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "1",
                "-storetype", "JKS",
                "-keystore", jks.toString(),
                "-storepass", storePassword,
                "-keypass", keyPassword,
                "-dname", "CN=jwt-test")
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException("keytool 生成测试 JKS 失败, exit="
                    + process.exitValue() + ", output=" + output);
        }
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
