package cn.miniants.platform.security.sas.keystore;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 可热替换的 {@link JWKSource}。快照里只有 active 带私钥并排在首位，
 * 供 {@code NimbusJwtEncoder} 签发；其余条目只放公钥供验签与 {@code /rsa/publicKey}。
 *
 * <p>Spring Security 7 的 RSA matcher <em>不</em>筛私钥，多把 RSA 匹配时默认抛错。
 * 装配签发器时必须 {@code encoder.setJwkSelector(RotatableJwkSource::selectSigningJwk)}。
 */
public final class RotatableJwkSource implements JWKSource<SecurityContext> {

    private final AtomicReference<JwtKeyStore> store = new AtomicReference<>();
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.empty());
    private final JwtKeyStore fallback;

    public RotatableJwkSource(JwtKeyStore store) {
        this(store, null);
    }

    public RotatableJwkSource(JwtKeyStore store, JwtKeyStore fallback) {
        this.fallback = fallback;
        refresh(store);
    }

    /**
     * 用当前绑定的 {@link JwtKeyStore} 重建快照（admin 写库后、多实例通知时调用）。
     */
    public void reload() {
        refresh(store.get());
    }

    /**
     * 换成新的 store 并原子替换快照。store 为空则走构造时的兜底 store。
     */
    public void refresh(JwtKeyStore next) {
        store.set(next);
        snapshot.set(Snapshot.from(next, fallback));
    }

    public JWKSet currentJwkSet() {
        return snapshot.get().jwkSet();
    }

    public String activeKid() {
        return snapshot.get().activeKid();
    }

    @Override
    public List<JWK> get(JWKSelector jwkSelector, SecurityContext context) {
        return jwkSelector.select(snapshot.get().jwkSet());
    }

    /**
     * 在匹配结果里选出唯一一把带私钥的 JWK。给 {@code NimbusJwtEncoder#setJwkSelector} 用。
     */
    public static JWK selectSigningJwk(List<JWK> matches) {
        if (matches == null || matches.isEmpty()) {
            throw new IllegalStateException("没有可用的 JWT 签发密钥");
        }
        JWK privateJwk = null;
        for (JWK jwk : matches) {
            if (jwk.isPrivate()) {
                if (privateJwk != null) {
                    throw new IllegalStateException("匹配到多把签发私钥");
                }
                privateJwk = jwk;
            }
        }
        if (privateJwk == null) {
            throw new IllegalStateException("匹配的 JWK 均无私钥，无法签发");
        }
        return privateJwk;
    }

    private record Snapshot(JWKSet jwkSet, String activeKid) {

        static Snapshot empty() {
            return new Snapshot(new JWKSet(), null);
        }

        static Snapshot from(JwtKeyStore primary, JwtKeyStore fallback) {
            JwtKeyStore effective = resolveStore(primary, fallback);
            List<JwtKeyEntry> entries = effective.list();
            if (entries == null || entries.isEmpty()) {
                throw new IllegalStateException("JWT 密钥库为空，无法签发");
            }
            JwtKeyEntry active = effective.loadActive();
            if (active == null) {
                active = entries.stream().filter(JwtKeyEntry::active).findFirst().orElse(null);
            }
            if (active == null || !active.hasPrivateKey()) {
                throw new IllegalStateException("JWT 密钥库没有可用的签发私钥");
            }
            LinkedHashMap<String, JWK> byKid = new LinkedHashMap<>();
            byKid.put(active.kid(), toSigningJwk(active));
            for (JwtKeyEntry entry : entries) {
                if (active.kid().equals(entry.kid())) {
                    continue;
                }
                if (byKid.containsKey(entry.kid())) {
                    throw new IllegalStateException("JWK Set 中存在重复 kid: " + entry.kid());
                }
                byKid.put(entry.kid(), toVerifyJwk(entry));
            }
            return new Snapshot(new JWKSet(new ArrayList<>(byKid.values())), active.kid());
        }

        private static JwtKeyStore resolveStore(JwtKeyStore primary, JwtKeyStore fallback) {
            if (primary != null && primary.list() != null && !primary.list().isEmpty()) {
                return primary;
            }
            if (fallback != null && fallback.list() != null && !fallback.list().isEmpty()) {
                return fallback;
            }
            if (primary != null) {
                return primary;
            }
            if (fallback != null) {
                return fallback;
            }
            throw new IllegalStateException("JWT 密钥库为空，无法签发");
        }

        private static RSAKey toSigningJwk(JwtKeyEntry entry) {
            return new RSAKey.Builder(entry.rsaPublicKey())
                    .privateKey(entry.rsaPrivateKey())
                    .keyID(entry.kid())
                    .build();
        }

        private static RSAKey toVerifyJwk(JwtKeyEntry entry) {
            return new RSAKey.Builder(entry.rsaPublicKey())
                    .keyID(entry.kid())
                    .build();
        }
    }
}
