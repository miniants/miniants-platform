package cn.miniants.platform.security.sas.keystore;

import cn.miniants.platform.security.sas.JwtKeyStores;
import cn.miniants.platform.security.sas.PlatformSasProperties;
import org.springframework.core.io.ResourceLoader;

import java.security.KeyPair;
import java.util.List;

/**
 * 默认单钥存储：读 {@code platform.security.sas.keystore}（classpath JKS）。
 * 未配置 keystore 时集合为空，由 {@link RotatableJwkSource} 用临时 KeyPair 兜底。
 */
public final class ClasspathSingleKeyStore implements JwtKeyStore {

    private final List<JwtKeyEntry> entries;

    public ClasspathSingleKeyStore(JwtKeyEntry entry) {
        this.entries = entry == null ? List.of() : List.of(entry);
    }

    public ClasspathSingleKeyStore(KeyPair keyPair, String kid) {
        this(keyPair == null ? null : JwtKeyEntry.signing(kid, keyPair));
    }

    public ClasspathSingleKeyStore(PlatformSasProperties properties, ResourceLoader resourceLoader) {
        this(loadIfConfigured(properties, resourceLoader));
    }

    private static JwtKeyEntry loadIfConfigured(PlatformSasProperties properties, ResourceLoader resourceLoader) {
        if (properties == null || resourceLoader == null) {
            return null;
        }
        PlatformSasProperties.Keystore keystore = properties.getKeystore();
        if (!keystore.isConfigured()) {
            return null;
        }
        keystore.requireComplete();
        KeyPair keyPair = JwtKeyStores.load(
                resourceLoader.getResource(keystore.getLocation().trim()),
                keystore.getStorePassword(),
                keystore.getAlias().trim(),
                keystore.getKeyPassword());
        return JwtKeyEntry.signing(keystore.getAlias().trim(), keyPair);
    }

    @Override
    public List<JwtKeyEntry> list() {
        return entries;
    }

    @Override
    public JwtKeyEntry loadActive() {
        return entries.isEmpty() ? null : entries.get(0);
    }
}
