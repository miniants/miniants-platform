package cn.miniants.platform.admin.person;

import cn.miniants.platform.data.PlatformDataProperties;
import cn.miniants.platform.security.person.Person;
import cn.miniants.platform.security.person.PersonRegistry;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/**
 * JDBC {@link PersonRegistry}：按证件摘要验人、建人、合并。明文证件号不得写入日志。
 */
public class JdbcPersonRegistry implements PersonRegistry {

    private final JdbcTemplate jdbcTemplate;
    private final String personTable;
    private final String userTable;
    private final String externalIdentityTable;

    public JdbcPersonRegistry(JdbcTemplate jdbcTemplate, PlatformDataProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        String prefix = properties.jdbcTablePrefix();
        this.personTable = prefix + "person";
        this.userTable = prefix + "user";
        this.externalIdentityTable = prefix + "external_identity";
    }

    @Override
    public Optional<Person> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT id, real_name, id_doc_type, id_lookup_digest, verified_at FROM "
                            + personTable + " WHERE id = ? AND deleted IS NULL",
                    (rs, rowNum) -> mapPerson(rs),
                    id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Person> findByLookupDigest(String digest) {
        if (digest == null || digest.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT id, real_name, id_doc_type, id_lookup_digest, verified_at FROM "
                            + personTable + " WHERE id_lookup_digest = ? AND deleted IS NULL",
                    (rs, rowNum) -> mapPerson(rs),
                    digest));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public Person verifyOrCreate(String realName, String idDocType, String lookupDigest, String optionalCipher) {
        if (lookupDigest == null || lookupDigest.isBlank()) {
            throw new IllegalArgumentException("证件摘要不能为空");
        }
        String docType = (idDocType == null || idDocType.isBlank()) ? "id_card" : idDocType.trim();
        Optional<Person> existing = findByLookupDigest(lookupDigest);
        Instant now = Instant.now();
        if (existing.isPresent()) {
            Person person = existing.get();
            if (optionalCipher != null && !optionalCipher.isBlank()) {
                jdbcTemplate.update(
                        "UPDATE " + personTable
                                + " SET real_name = ?, id_doc_type = ?, verified_at = ?, id_cipher = ?, update_time = CURRENT_TIMESTAMP"
                                + " WHERE id = ? AND deleted IS NULL",
                        realName, docType, Timestamp.from(now), optionalCipher, person.id());
            } else {
                jdbcTemplate.update(
                        "UPDATE " + personTable
                                + " SET real_name = ?, id_doc_type = ?, verified_at = ?, update_time = CURRENT_TIMESTAMP"
                                + " WHERE id = ? AND deleted IS NULL",
                        realName, docType, Timestamp.from(now), person.id());
            }
            return findById(person.id()).orElseThrow(() -> new IllegalStateException("自然人更新后读取失败"));
        }
        long id = IdWorker.getId();
        try {
            jdbcTemplate.update(
                    "INSERT INTO " + personTable
                            + " (id, real_name, id_doc_type, id_lookup_digest, id_cipher, verified_at, version, create_by, create_time)"
                            + " VALUES (?, ?, ?, ?, ?, ?, 1, 'system', CURRENT_TIMESTAMP)",
                    id,
                    realName,
                    docType,
                    lookupDigest,
                    blankToNull(optionalCipher),
                    Timestamp.from(now));
        } catch (DuplicateKeyException ex) {
            return findByLookupDigest(lookupDigest)
                    .orElseThrow(() -> new IllegalStateException("自然人并发创建冲突后读取失败", ex));
        }
        return findById(id).orElseThrow(() -> new IllegalStateException("自然人创建后读取失败"));
    }

    @Override
    @Transactional
    public Person createUnverified(String realName) {
        long id = IdWorker.getId();
        jdbcTemplate.update(
                "INSERT INTO " + personTable
                        + " (id, real_name, id_doc_type, id_lookup_digest, id_cipher, verified_at, version, create_by, create_time)"
                        + " VALUES (?, ?, 'id_card', NULL, NULL, NULL, 1, 'system', CURRENT_TIMESTAMP)",
                id,
                blankToNull(realName));
        return findById(id).orElseThrow(() -> new IllegalStateException("自然人创建后读取失败"));
    }

    @Override
    @Transactional
    public Person merge(Long canonicalId, Long duplicateId) {
        if (canonicalId == null || duplicateId == null) {
            throw new IllegalArgumentException("合并自然人参数不能为空");
        }
        if (canonicalId.equals(duplicateId)) {
            return findById(canonicalId).orElseThrow(() -> new IllegalArgumentException("自然人不存在"));
        }
        Person canonical = findById(canonicalId)
                .orElseThrow(() -> new IllegalArgumentException("目标自然人不存在"));
        if (findById(duplicateId).isEmpty()) {
            throw new IllegalArgumentException("被合并自然人不存在");
        }

        jdbcTemplate.update(
                "UPDATE " + userTable + " SET person_id = ? WHERE person_id = ? AND deleted IS NULL",
                canonicalId, duplicateId);
        jdbcTemplate.update(
                "UPDATE " + externalIdentityTable + " SET person_id = ? WHERE person_id = ? AND deleted IS NULL",
                canonicalId, duplicateId);

        Integer leftoverUsers = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + userTable + " WHERE person_id = ? AND deleted IS NULL",
                Integer.class, duplicateId);
        Integer leftoverIdentities = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + externalIdentityTable + " WHERE person_id = ? AND deleted IS NULL",
                Integer.class, duplicateId);
        if ((leftoverUsers != null && leftoverUsers > 0)
                || (leftoverIdentities != null && leftoverIdentities > 0)) {
            throw new IllegalStateException("合并自然人后仍有账号或外部身份指向被合并行，禁止孤儿引用");
        }

        int deleted = jdbcTemplate.update(
                "UPDATE " + personTable
                        + " SET deleted = CURRENT_TIMESTAMP, update_time = CURRENT_TIMESTAMP"
                        + " WHERE id = ? AND deleted IS NULL",
                duplicateId);
        if (deleted == 0) {
            throw new IllegalStateException("软删除被合并自然人失败");
        }
        return canonical;
    }

    private static Person mapPerson(ResultSet rs) throws SQLException {
        Timestamp verified = rs.getTimestamp("verified_at");
        return new Person(
                rs.getLong("id"),
                rs.getString("real_name"),
                rs.getString("id_doc_type"),
                rs.getString("id_lookup_digest"),
                verified == null ? null : verified.toInstant());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
