package cn.miniants.platform.admin.keystore;

import cn.miniants.platform.data.PlatformDataProperties;
import cn.miniants.platform.security.sas.PlatformSasProperties;
import cn.miniants.platform.security.sas.keystore.JwtKeyEntry;
import cn.miniants.platform.security.sas.keystore.JwtKeyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * JDBC {@link JwtKeyStore}。空库或表尚未迁出时返回空集合，由 {@code RotatableJwkSource} 走 classpath 兜底。
 * 仅 {@code is_active=1} 行解密私钥；已摘旧不进验签集。
 */
public class JdbcJwtKeyStore implements JwtKeyStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcJwtKeyStore.class);

    private final JdbcTemplate jdbcTemplate;
    private final AesGcmPrivateKeyWrap wrap;
    private final String table;
    private final Supplier<KeyPair> fallbackPair;
    private final Supplier<PlatformSasProperties> sasProperties;

    public JdbcJwtKeyStore(
            JdbcTemplate jdbcTemplate,
            AesGcmPrivateKeyWrap wrap,
            PlatformDataProperties dataProperties,
            KeyPair fallbackPair,
            PlatformSasProperties sasProperties) {
        this(jdbcTemplate, wrap, dataProperties, () -> fallbackPair, () -> sasProperties);
    }

    public JdbcJwtKeyStore(
            JdbcTemplate jdbcTemplate,
            AesGcmPrivateKeyWrap wrap,
            PlatformDataProperties dataProperties,
            Supplier<KeyPair> fallbackPair,
            Supplier<PlatformSasProperties> sasProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.wrap = wrap;
        this.table = dataProperties.jdbcTablePrefix() + "jwt_keystore";
        this.fallbackPair = fallbackPair == null ? () -> null : fallbackPair;
        this.sasProperties = sasProperties == null ? () -> null : sasProperties;
    }

    @Override
    public List<JwtKeyEntry> list() {
        List<Row> rows;
        try {
            rows = jdbcTemplate.query(
                    "SELECT kid, algorithm, public_key, private_key_cipher, is_active FROM " + table
                            + " WHERE deleted IS NULL AND status <> ? ORDER BY is_active DESC, id ASC",
                    (rs, rowNum) -> new Row(
                            rs.getString("kid"),
                            rs.getString("algorithm"),
                            rs.getString("public_key"),
                            rs.getString("private_key_cipher"),
                            rs.getInt("is_active") == 1),
                    JwtKeystoreStatuses.RETIRED);
        } catch (DataAccessException ex) {
            if (missingTable(ex)) {
                log.warn("JWT 密钥表 {} 尚不存在，回落 classpath 签发", table);
                return List.of();
            }
            throw ex;
        }
        if (rows.isEmpty()) {
            return List.of();
        }
        List<JwtKeyEntry> entries = new ArrayList<>();
        JwtKeyEntry dbActive = null;
        for (Row row : rows) {
            JwtKeyEntry entry = toEntry(row);
            if (entry == null) {
                continue;
            }
            if (entry.active() && entry.hasPrivateKey()) {
                dbActive = entry;
            }
            entries.add(entry);
        }
        if (entries.isEmpty()) {
            return List.of();
        }
        if (dbActive != null) {
            addFallbackVerifyOnlyIfAbsent(entries);
            return List.copyOf(entries);
        }
        JwtKeyEntry fallback = fallbackSigning();
        if (fallback == null) {
            return List.of();
        }
        List<JwtKeyEntry> merged = new ArrayList<>();
        merged.add(fallback);
        for (JwtKeyEntry entry : entries) {
            if (!fallback.kid().equals(entry.kid())) {
                merged.add(entry);
            }
        }
        return List.copyOf(merged);
    }

    @Override
    public JwtKeyEntry loadActive() {
        List<Row> rows;
        try {
            rows = jdbcTemplate.query(
                    "SELECT kid, algorithm, public_key, private_key_cipher, is_active FROM " + table
                            + " WHERE deleted IS NULL AND is_active = 1",
                    (rs, rowNum) -> new Row(
                            rs.getString("kid"),
                            rs.getString("algorithm"),
                            rs.getString("public_key"),
                            rs.getString("private_key_cipher"),
                            true));
        } catch (DataAccessException ex) {
            if (missingTable(ex)) {
                return null;
            }
            throw ex;
        }
        if (!rows.isEmpty()) {
            JwtKeyEntry active = toEntry(rows.get(0));
            if (active != null && active.hasPrivateKey()) {
                return active;
            }
            return null;
        }
        if (hasUsableVerifyOnlyRows()) {
            return fallbackSigning();
        }
        return null;
    }

    private boolean hasUsableVerifyOnlyRows() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE deleted IS NULL AND status <> ?",
                Integer.class,
                JwtKeystoreStatuses.RETIRED);
        return count != null && count > 0;
    }

    private JwtKeyEntry toEntry(Row row) {
        PublicKey publicKey;
        try {
            publicKey = JwtPemKeys.decodePublic(row.publicKey);
        } catch (RuntimeException ex) {
            return null;
        }
        if (row.active) {
            return wrap.tryDecrypt(row.privateKeyCipher)
                    .map(bytes -> {
                        PrivateKey privateKey = JwtPemKeys.decodePrivatePkcs8(bytes);
                        return new JwtKeyEntry(row.kid, publicKey, privateKey, true, row.algorithm);
                    })
                    .orElse(null);
        }
        return JwtKeyEntry.verifyOnly(row.kid, publicKey, row.algorithm);
    }

    private JwtKeyEntry fallbackSigning() {
        KeyPair pair = fallbackPair.get();
        String kid = fallbackKid(sasProperties.get());
        if (pair == null || kid == null || kid.isBlank()) {
            return null;
        }
        return JwtKeyEntry.signing(kid, pair);
    }

    private void addFallbackVerifyOnlyIfAbsent(List<JwtKeyEntry> entries) {
        JwtKeyEntry fallback = fallbackSigning();
        if (fallback == null) {
            return;
        }
        boolean present = entries.stream().anyMatch(entry -> fallback.kid().equals(entry.kid()));
        if (present || fallbackRetired(fallback.kid())) {
            return;
        }
        entries.add(JwtKeyEntry.verifyOnly(fallback.kid(), fallback.publicKey(), fallback.algorithm()));
    }

    private boolean fallbackRetired(String kid) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE kid = ? AND deleted IS NULL AND status = ?",
                Integer.class,
                kid,
                JwtKeystoreStatuses.RETIRED);
        return count != null && count > 0;
    }

    static boolean missingTable(DataAccessException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String sqlState = null;
        if (cause instanceof java.sql.SQLException sql) {
            sqlState = sql.getSQLState();
        }
        String message = cause.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        boolean jwtTable = lower.contains("jwt_keystore") || lower.contains("jwt_wrap");
        if (sqlState != null && sqlState.startsWith("42")) {
            return jwtTable;
        }
        return jwtTable
                && (lower.contains("doesn't exist")
                || lower.contains("does not exist")
                || lower.contains("not found"));
    }

    public static String fallbackKid(PlatformSasProperties sasProperties) {
        if (sasProperties == null) {
            return "jwt";
        }
        String alias = sasProperties.getKeystore().getAlias();
        if (alias != null && !alias.isBlank()) {
            return alias.trim();
        }
        return "jwt";
    }

    private record Row(String kid, String algorithm, String publicKey, String privateKeyCipher, boolean active) {
    }
}
