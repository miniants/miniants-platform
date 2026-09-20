package cn.miniants.platform.admin.service;

import cn.miniants.platform.admin.dto.JwtKeystoreVo;
import cn.miniants.platform.admin.dto.JwtWrapSettingsVo;
import cn.miniants.platform.admin.dto.PageResult;
import cn.miniants.platform.admin.entity.JwtKeystore;
import cn.miniants.platform.admin.keystore.AesGcmPrivateKeyWrap;
import cn.miniants.platform.admin.keystore.JdbcJwtKeyStore;
import cn.miniants.platform.admin.keystore.JwtWrapKeyResolver;
import cn.miniants.platform.admin.keystore.JwtKeystoreChangedEvent;
import cn.miniants.platform.admin.keystore.JwtKeystoreStatuses;
import cn.miniants.platform.admin.keystore.JwtPemKeys;
import cn.miniants.platform.admin.mapper.JwtKeystoreMapper;
import cn.miniants.platform.admin.support.EntityPages;
import cn.miniants.platform.admin.support.PageQueries;
import cn.miniants.platform.core.error.PlatformException;
import cn.miniants.platform.data.PlatformDataProperties;
import cn.miniants.platform.security.CurrentUser;
import cn.miniants.platform.security.sas.PlatformSasProperties;
import cn.miniants.platform.security.sas.keystore.JwtKeyEntry;
import cn.miniants.platform.security.sas.keystore.JwtKids;
import cn.miniants.platform.security.sas.keystore.RotatableJwkSource;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public class DefaultJwtKeystoreAdminService implements JwtKeystoreAdminService {

    private final JwtKeystoreMapper jwtKeystoreMapper;
    private final JdbcTemplate jdbcTemplate;
    private final AesGcmPrivateKeyWrap wrap;
    private final String table;
    private final Supplier<PlatformSasProperties> sasProperties;
    private final Supplier<KeyPair> fallbackPair;
    private final Supplier<RotatableJwkSource> jwkSource;
    private final ApplicationEventPublisher eventPublisher;
    private final JwtWrapKeyResolver wrapKeyResolver;

    public DefaultJwtKeystoreAdminService(
            JwtKeystoreMapper jwtKeystoreMapper,
            JdbcTemplate jdbcTemplate,
            AesGcmPrivateKeyWrap wrap,
            PlatformDataProperties dataProperties,
            PlatformSasProperties sasProperties,
            KeyPair fallbackPair,
            RotatableJwkSource jwkSource,
            ApplicationEventPublisher eventPublisher) {
        this(jwtKeystoreMapper, jdbcTemplate, wrap, dataProperties,
                sasProperties, fallbackPair, jwkSource, eventPublisher, null);
    }

    public DefaultJwtKeystoreAdminService(
            JwtKeystoreMapper jwtKeystoreMapper,
            JdbcTemplate jdbcTemplate,
            AesGcmPrivateKeyWrap wrap,
            PlatformDataProperties dataProperties,
            PlatformSasProperties sasProperties,
            KeyPair fallbackPair,
            RotatableJwkSource jwkSource,
            ApplicationEventPublisher eventPublisher,
            JwtWrapKeyResolver wrapKeyResolver) {
        this(jwtKeystoreMapper, jdbcTemplate, wrap, dataProperties,
                () -> sasProperties, () -> fallbackPair, () -> jwkSource, eventPublisher, wrapKeyResolver);
    }

    public DefaultJwtKeystoreAdminService(
            JwtKeystoreMapper jwtKeystoreMapper,
            JdbcTemplate jdbcTemplate,
            AesGcmPrivateKeyWrap wrap,
            PlatformDataProperties dataProperties,
            ObjectProvider<PlatformSasProperties> sasProperties,
            ObjectProvider<KeyPair> fallbackPair,
            ObjectProvider<RotatableJwkSource> jwkSource,
            ApplicationEventPublisher eventPublisher,
            JwtWrapKeyResolver wrapKeyResolver) {
        this(jwtKeystoreMapper, jdbcTemplate, wrap, dataProperties,
                sasProperties::getIfAvailable, fallbackPair::getIfAvailable, jwkSource::getIfAvailable,
                eventPublisher, wrapKeyResolver);
    }

    public DefaultJwtKeystoreAdminService(
            JwtKeystoreMapper jwtKeystoreMapper,
            JdbcTemplate jdbcTemplate,
            AesGcmPrivateKeyWrap wrap,
            PlatformDataProperties dataProperties,
            Supplier<PlatformSasProperties> sasProperties,
            Supplier<KeyPair> fallbackPair,
            Supplier<RotatableJwkSource> jwkSource,
            ApplicationEventPublisher eventPublisher,
            JwtWrapKeyResolver wrapKeyResolver) {
        this.jwtKeystoreMapper = jwtKeystoreMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.wrap = wrap;
        this.table = dataProperties.jdbcTablePrefix() + "jwt_keystore";
        this.sasProperties = sasProperties == null ? () -> null : sasProperties;
        this.fallbackPair = fallbackPair == null ? () -> null : fallbackPair;
        this.jwkSource = jwkSource == null ? () -> null : jwkSource;
        this.eventPublisher = eventPublisher;
        this.wrapKeyResolver = wrapKeyResolver;
    }

    @Override
    public PageResult<JwtKeystoreVo> page(Long current, Long size, String filter, String order) {
        if (jwtKeystoreMapper != null) {
            Page<JwtKeystore> page = jwtKeystoreMapper.selectPage(
                    new Page<>(PageQueries.current(current), PageQueries.size(size)),
                    EntityPages.wrapper(JwtKeystore.class, filter, order, "privateKeyCipher"));
            return PageResult.of(page, page.getRecords().stream().map(DefaultJwtKeystoreAdminService::toVo).toList());
        }
        return slice(list(), current, size);
    }

    @Override
    public List<JwtKeystoreVo> list() {
        return jdbcTemplate.query(
                "SELECT id, kid, algorithm, public_key, status, is_active, activated_at, retired_at, version, create_time FROM "
                        + table + " WHERE deleted IS NULL ORDER BY is_active DESC, id DESC",
                voMapper());
    }

    @Override
    public JwtWrapSettingsVo wrapSettings() {
        return toWrapVo(requireResolver().snapshot());
    }

    @Override
    @Transactional
    public JwtWrapSettingsVo saveWrapSource(String source) {
        return toWrapVo(requireResolver().saveSource(source));
    }

    @Override
    @Transactional
    public JwtKeystoreVo generate() {
        wrap.requireAvailable();
        seedFallbackPublicIfAbsent();
        KeyPair pair = generateRsa();
        String kid = nextUniqueKid();
        String cipher = wrap.encrypt(pair.getPrivate().getEncoded());
        long id = IdWorker.getId();
        String auditor = auditorName();
        try {
            jdbcTemplate.update(
                    "INSERT INTO " + table
                            + " (id, kid, algorithm, public_key, private_key_cipher, status, is_active, version,"
                            + " create_by, create_time) VALUES (?, ?, ?, ?, ?, ?, 0, 1, ?, CURRENT_TIMESTAMP)",
                    id,
                    kid,
                    JwtKeyEntry.ALG_RS256,
                    JwtPemKeys.encodePublicPem(pair.getPublic()),
                    cipher,
                    JwtKeystoreStatuses.VERIFY_ONLY,
                    auditor);
        } catch (DuplicateKeyException ex) {
            throw new PlatformException("JWT kid 已存在，请重试生成");
        }
        afterWrite(kid, JwtKeystoreChangedEvent.ACTION_GENERATE);
        return requireVo(kid);
    }

    @Override
    @Transactional
    public JwtKeystoreVo activate(String kid) {
        wrap.requireAvailable();
        String target = requireKid(kid);
        Row row = requireRow(target);
        if (row.privateKeyCipher == null || row.privateKeyCipher.isBlank()) {
            throw new PlatformException("该密钥没有私钥，无法激活签发");
        }
        if (wrap.tryDecrypt(row.privateKeyCipher).isEmpty()) {
            throw new PlatformException("该密钥私钥无法解密，无法激活签发");
        }
        String auditor = auditorName();
        jdbcTemplate.update(
                "UPDATE " + table
                        + " SET is_active = 0, status = CASE WHEN status = ? THEN ? ELSE status END,"
                        + " version = version + 1, update_by = ?, update_time = CURRENT_TIMESTAMP"
                        + " WHERE is_active = 1 AND deleted IS NULL AND kid <> ?",
                JwtKeystoreStatuses.ACTIVE,
                JwtKeystoreStatuses.VERIFY_ONLY,
                auditor,
                target);
        int updated = jdbcTemplate.update(
                "UPDATE " + table
                        + " SET is_active = 1, status = ?, activated_at = CURRENT_TIMESTAMP,"
                        + " version = version + 1, update_by = ?, update_time = CURRENT_TIMESTAMP"
                        + " WHERE kid = ? AND deleted IS NULL",
                JwtKeystoreStatuses.ACTIVE,
                auditor,
                target);
        if (updated == 0) {
            throw new PlatformException("JWT 密钥不存在");
        }
        afterWrite(target, JwtKeystoreChangedEvent.ACTION_ACTIVATE);
        return requireVo(target);
    }

    @Override
    @Transactional
    public JwtKeystoreVo retire(String kid) {
        wrap.requireAvailable();
        String target = requireKid(kid);
        Row row = requireRow(target);
        if (row.active) {
            throw new PlatformException("不能摘除当前签发密钥，请先激活另一把");
        }
        String auditor = auditorName();
        int updated = jdbcTemplate.update(
                "UPDATE " + table
                        + " SET status = ?, retired_at = CURRENT_TIMESTAMP, is_active = 0,"
                        + " version = version + 1, update_by = ?, update_time = CURRENT_TIMESTAMP"
                        + " WHERE kid = ? AND deleted IS NULL",
                JwtKeystoreStatuses.RETIRED,
                auditor,
                target);
        if (updated == 0) {
            throw new PlatformException("JWT 密钥不存在");
        }
        afterWrite(target, JwtKeystoreChangedEvent.ACTION_RETIRE);
        return requireVo(target);
    }

    private void seedFallbackPublicIfAbsent() {
        KeyPair pair = fallbackPair.get();
        if (pair == null) {
            return;
        }
        String kid = JdbcJwtKeyStore.fallbackKid(sasProperties.get());
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE kid = ? AND deleted IS NULL",
                Integer.class,
                kid);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO " + table
                        + " (id, kid, algorithm, public_key, private_key_cipher, status, is_active, version,"
                        + " create_by, create_time) VALUES (?, ?, ?, ?, NULL, ?, 0, 1, ?, CURRENT_TIMESTAMP)",
                IdWorker.getId(),
                kid,
                JwtKeyEntry.ALG_RS256,
                JwtPemKeys.encodePublicPem(pair.getPublic()),
                JwtKeystoreStatuses.VERIFY_ONLY,
                auditorName());
    }

    private void afterWrite(String kid, String action) {
        RotatableJwkSource source = jwkSource.get();
        if (source != null) {
            source.reload();
        }
        if (eventPublisher == null) {
            return;
        }
        JwtKeystoreChangedEvent event = new JwtKeystoreChangedEvent(this, kid, action);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventPublisher.publishEvent(event);
                }
            });
        } else {
            eventPublisher.publishEvent(event);
        }
    }

    private JwtKeystoreVo requireVo(String kid) {
        List<JwtKeystoreVo> found = jdbcTemplate.query(
                "SELECT id, kid, algorithm, public_key, status, is_active, activated_at, retired_at, version, create_time FROM "
                        + table + " WHERE kid = ? AND deleted IS NULL",
                voMapper(),
                kid);
        if (found.isEmpty()) {
            throw new PlatformException("JWT 密钥不存在");
        }
        return found.get(0);
    }

    private Row requireRow(String kid) {
        List<Row> found = jdbcTemplate.query(
                "SELECT kid, private_key_cipher, is_active FROM " + table + " WHERE kid = ? AND deleted IS NULL",
                (rs, rowNum) -> new Row(
                        rs.getString("kid"),
                        rs.getString("private_key_cipher"),
                        rs.getInt("is_active") == 1),
                kid);
        if (found.isEmpty()) {
            throw new PlatformException("JWT 密钥不存在");
        }
        return found.get(0);
    }

    private static RowMapper<JwtKeystoreVo> voMapper() {
        return (rs, rowNum) -> toVo(
                rs.getLong("id"),
                rs.getString("kid"),
                rs.getString("algorithm"),
                rs.getString("public_key"),
                rs.getString("status"),
                rs.getInt("is_active") == 1,
                localDateTime(rs, "activated_at"),
                localDateTime(rs, "retired_at"),
                rs.getLong("version"),
                localDateTime(rs, "create_time"));
    }

    private static JwtKeystoreVo toVo(JwtKeystore entity) {
        return toVo(
                entity.getId(),
                entity.getKid(),
                entity.getAlgorithm(),
                entity.getPublicKey(),
                entity.getStatus(),
                entity.getIsActive() != null && entity.getIsActive() == 1,
                entity.getActivatedAt(),
                entity.getRetiredAt(),
                entity.getVersion(),
                entity.getCreateTime());
    }

    private static JwtKeystoreVo toVo(
            Long id,
            String kid,
            String algorithm,
            String publicKeyPem,
            String status,
            boolean active,
            LocalDateTime activatedAt,
            LocalDateTime retiredAt,
            Long version,
            LocalDateTime createTime) {
        JwtKeystoreVo vo = new JwtKeystoreVo();
        vo.setId(id);
        vo.setKid(kid);
        vo.setAlgorithm(algorithm);
        vo.setFingerprint(fingerprintSafe(publicKeyPem));
        vo.setStatus(status);
        vo.setActive(active);
        vo.setActivatedAt(activatedAt);
        vo.setRetiredAt(retiredAt);
        vo.setVersion(version);
        vo.setCreateTime(createTime);
        return vo;
    }

    private static String fingerprintSafe(String publicKeyPem) {
        try {
            PublicKey publicKey = JwtPemKeys.decodePublic(publicKeyPem);
            return JwtPemKeys.fingerprint(publicKey);
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static PageResult<JwtKeystoreVo> slice(List<JwtKeystoreVo> records, Long current, Long size) {
        long cur = PageQueries.current(current);
        long sz = PageQueries.size(size);
        int from = (int) Math.min((cur - 1) * sz, records.size());
        int to = (int) Math.min(from + sz, records.size());
        PageResult<JwtKeystoreVo> result = new PageResult<>();
        result.setRecords(records.subList(from, to));
        result.setTotal(records.size());
        result.setCurrent(cur);
        result.setSize(sz);
        return result;
    }

    private JwtWrapKeyResolver requireResolver() {
        if (wrapKeyResolver == null) {
            throw new PlatformException("JWT 包装密钥解析器未装配");
        }
        return wrapKeyResolver;
    }

    private static JwtWrapSettingsVo toWrapVo(JwtWrapKeyResolver.JwtWrapSettingsSnapshot snapshot) {
        JwtWrapSettingsVo vo = new JwtWrapSettingsVo();
        vo.setSource(snapshot.source());
        vo.setEnvConfigured(snapshot.envConfigured());
        vo.setDatabaseReady(snapshot.databaseReady());
        return vo;
    }

    private static String requireKid(String kid) {
        if (kid == null || kid.isBlank()) {
            throw new PlatformException("JWT kid 不能为空");
        }
        return kid.trim();
    }

    private String nextUniqueKid() {
        String base = JwtKids.next(kidPrefix(), keystoreAlias());
        if (!kidExists(base)) {
            return base;
        }
        for (int i = 0; i < 8; i++) {
            String candidate = base + "-" + ThreadLocalRandom.current().nextInt(1000, 10000);
            if (!kidExists(candidate)) {
                return candidate;
            }
        }
        throw new PlatformException("JWT kid 已存在，请重试生成");
    }

    private boolean kidExists(String kid) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE kid = ? AND deleted IS NULL",
                Integer.class,
                kid);
        return count != null && count > 0;
    }

    private String kidPrefix() {
        PlatformSasProperties properties = sasProperties.get();
        return properties == null ? "" : properties.getKidPrefix();
    }

    private String keystoreAlias() {
        PlatformSasProperties properties = sasProperties.get();
        if (properties == null) {
            return "";
        }
        return properties.getKeystore().getAlias();
    }

    private static String auditorName() {
        CurrentUser user = CurrentUser.find();
        if (user != null && user.username() != null && !user.username().isBlank()) {
            return user.username();
        }
        return "system";
    }

    private static KeyPair generateRsa() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new PlatformException("无法生成 JWT 密钥对");
        }
    }

    private static LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private record Row(String kid, String privateKeyCipher, boolean active) {
    }
}
