package cn.miniants.platform.security.sas.keystore;

import java.util.ArrayList;
import java.util.List;

/**
 * 单测用可变密钥库。生产实现由 platform-admin 提供。
 */
final class InMemoryJwtKeyStore implements JwtKeyStore {

    private final List<JwtKeyEntry> entries = new ArrayList<>();

    InMemoryJwtKeyStore(JwtKeyEntry... initial) {
        entries.addAll(List.of(initial));
    }

    void replace(JwtKeyEntry... next) {
        entries.clear();
        entries.addAll(List.of(next));
    }

    @Override
    public List<JwtKeyEntry> list() {
        return List.copyOf(entries);
    }

    @Override
    public JwtKeyEntry loadActive() {
        return entries.stream().filter(JwtKeyEntry::active).findFirst().orElse(null);
    }
}
