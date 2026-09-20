package cn.miniants.platform.admin.keystore;

import cn.miniants.platform.core.error.PlatformException;
import cn.miniants.platform.data.PlatformDataProperties;
import cn.miniants.platform.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * 包装密钥解析：env（配置 / {@code PLATFORM_JWT_WRAP_KEY}）或库内一行。
 * 库内密钥不进日志、不进返回值。
 */
public class JwtWrapKeyResolver {

    static final long SETTINGS_ID = 1L;

    private static final Logger log = LoggerFactory.getLogger(JwtWrapKeyResolver.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;
    private final String table;
    private final PlatformJwtKeystoreProperties properties;

    public JwtWrapKeyResolver(
            JdbcTemplate jdbcTemplate,
            PlatformDataProperties dataProperties,
            PlatformJwtKeystoreProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.table = dataProperties.jdbcTablePrefix() + "jwt_wrap_settings";
        this.properties = properties == null ? new PlatformJwtKeystoreProperties() : properties;
    }

    public String currentSource() {
        Row row = loadRow();
        if (row != null && row.wrapSource != null && !row.wrapSource.isBlank()) {
            return JwtWrapSources.normalize(row.wrapSource);
        }
        return JwtWrapSources.normalize(properties.getWrapSource());
    }

    public String resolveBase64(boolean ensureDatabaseKey) {
        String source = currentSource();
        if (JwtWrapSources.ENV.equals(source)) {
            return envKey();
        }
        String stored = rowWrapKey(loadRow());
        if ((stored == null || stored.isBlank()) && ensureDatabaseKey) {
            stored = createDatabaseKey();
        }
        return stored == null ? "" : stored;
    }

    public JwtWrapSettingsSnapshot snapshot() {
        String source = currentSource();
        String env = envKey();
        String db = rowWrapKey(loadRow());
        return new JwtWrapSettingsSnapshot(
                source,
                env != null && !env.isBlank(),
                db != null && !db.isBlank());
    }

    public JwtWrapSettingsSnapshot saveSource(String rawSource) {
        String source = JwtWrapSources.normalize(rawSource);
        upsertSource(source);
        if (JwtWrapSources.DATABASE.equals(source)) {
            Row row = loadRow();
            if (rowWrapKey(row).isBlank()) {
                createDatabaseKey();
            }
        }
        log.info("JWT 包装密钥来源已切换为 {}", source);
        return snapshot();
    }

    private String envKey() {
        String configured = properties.getWrapKey();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        String env = System.getenv(AesGcmPrivateKeyWrap.ENV_WRAP_KEY);
        return env == null ? "" : env.trim();
    }

    private String createDatabaseKey() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String key = Base64.getEncoder().encodeToString(raw);
        upsertKey(key);
        log.info("已在库内生成 JWT 包装密钥（不回显）");
        return key;
    }

    private void upsertSource(String source) {
        try {
            upsertSourceInternal(source);
        } catch (DataAccessException ex) {
            throw settingsWriteFailed(ex);
        }
    }

    private void upsertSourceInternal(String source) {
        String auditor = auditorName();
        int updated = jdbcTemplate.update(
                "UPDATE " + table
                        + " SET wrap_source = ?, version = version + 1, update_by = ?, update_time = CURRENT_TIMESTAMP"
                        + " WHERE id = ?",
                source,
                auditor,
                SETTINGS_ID);
        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO " + table + " (id, wrap_source, wrap_key, version, update_by, update_time)"
                            + " VALUES (?, ?, NULL, 1, ?, CURRENT_TIMESTAMP)",
                    SETTINGS_ID,
                    source,
                    auditor);
        }
    }

    private void upsertKey(String key) {
        try {
            upsertKeyInternal(key);
        } catch (DataAccessException ex) {
            throw settingsWriteFailed(ex);
        }
    }

    private void upsertKeyInternal(String key) {
        String auditor = auditorName();
        int updated = jdbcTemplate.update(
                "UPDATE " + table
                        + " SET wrap_key = ?, version = version + 1, update_by = ?, update_time = CURRENT_TIMESTAMP"
                        + " WHERE id = ?",
                key,
                auditor,
                SETTINGS_ID);
        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO " + table + " (id, wrap_source, wrap_key, version, update_by, update_time)"
                            + " VALUES (?, ?, ?, 1, ?, CURRENT_TIMESTAMP)",
                    SETTINGS_ID,
                    JwtWrapSources.DATABASE,
                    key,
                    auditor);
        }
    }

    private Row loadRow() {
        try {
            List<Row> rows = jdbcTemplate.query(
                    "SELECT wrap_source, wrap_key FROM " + table + " WHERE id = ?",
                    (rs, rowNum) -> new Row(rs.getString("wrap_source"), rs.getString("wrap_key")),
                    SETTINGS_ID);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (DataAccessException ex) {
            if (JdbcJwtKeyStore.missingTable(ex)) {
                return null;
            }
            throw new PlatformException("无法读取 JWT 包装密钥设置");
        }
    }

    private static PlatformException settingsWriteFailed(DataAccessException ex) {
        if (JdbcJwtKeyStore.missingTable(ex)) {
            return new PlatformException("JWT 包装设置表不存在，请先执行业务库迁移后再用来源「本库」");
        }
        return new PlatformException("无法保存 JWT 包装密钥设置");
    }

    private static String rowWrapKey(Row row) {
        if (row == null || row.wrapKey == null) {
            return "";
        }
        return row.wrapKey.trim();
    }

    private static String auditorName() {
        CurrentUser user = CurrentUser.find();
        if (user != null && user.username() != null && !user.username().isBlank()) {
            return user.username();
        }
        return "system";
    }

    public record JwtWrapSettingsSnapshot(String source, boolean envConfigured, boolean databaseReady) {
    }

    private record Row(String wrapSource, String wrapKey) {
    }
}
