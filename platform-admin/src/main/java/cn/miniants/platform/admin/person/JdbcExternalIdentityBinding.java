package cn.miniants.platform.admin.person;

import cn.miniants.platform.data.PlatformDataProperties;
import cn.miniants.platform.security.identity.ExternalIdentity;
import cn.miniants.platform.security.identity.ExternalIdentityBinding;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * JDBC {@link ExternalIdentityBinding}：{@code (provider, subject) ↔ person}，UNIQUE 冲突时改绑或复活软删行。
 */
public class JdbcExternalIdentityBinding implements ExternalIdentityBinding {

    private final JdbcTemplate jdbcTemplate;
    private final String table;

    public JdbcExternalIdentityBinding(JdbcTemplate jdbcTemplate, PlatformDataProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        String prefix = properties.jdbcTablePrefix();
        this.table = prefix + "external_identity";
    }

    @Override
    public Optional<ExternalIdentity> find(String provider, String subject) {
        if (provider == null || provider.isBlank() || subject == null || subject.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT provider, subject, person_id FROM " + table
                            + " WHERE provider = ? AND subject = ? AND deleted IS NULL",
                    (rs, rowNum) -> new ExternalIdentity(
                            rs.getString("provider"),
                            rs.getString("subject"),
                            rs.getLong("person_id")),
                    provider,
                    subject));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ExternalIdentity> findByPerson(String provider, Long personId) {
        if (provider == null || provider.isBlank() || personId == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT provider, subject, person_id FROM " + table
                            + " WHERE provider = ? AND person_id = ? AND deleted IS NULL"
                            + " ORDER BY id LIMIT 1",
                    (rs, rowNum) -> new ExternalIdentity(
                            rs.getString("provider"),
                            rs.getString("subject"),
                            rs.getLong("person_id")),
                    provider,
                    personId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public ExternalIdentity bind(String provider, String subject, Long personId) {
        if (provider == null || provider.isBlank() || subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("外部身份 provider/subject 不能为空");
        }
        if (personId == null) {
            throw new IllegalArgumentException("personId 不能为空");
        }
        Optional<ExternalIdentity> active = find(provider, subject);
        if (active.isPresent()) {
            jdbcTemplate.update(
                    "UPDATE " + table
                            + " SET person_id = ?, update_time = CURRENT_TIMESTAMP WHERE provider = ? AND subject = ? AND deleted IS NULL",
                    personId, provider, subject);
            return new ExternalIdentity(provider, subject, personId);
        }
        // 可能存在软删行占 UNIQUE：复活并改绑
        Integer any = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE provider = ? AND subject = ?",
                Integer.class, provider, subject);
        if (any != null && any > 0) {
            jdbcTemplate.update(
                    "UPDATE " + table
                            + " SET person_id = ?, deleted = NULL, update_time = CURRENT_TIMESTAMP"
                            + " WHERE provider = ? AND subject = ?",
                    personId, provider, subject);
            return new ExternalIdentity(provider, subject, personId);
        }
        try {
            jdbcTemplate.update(
                    "INSERT INTO " + table
                            + " (id, provider, subject, person_id, version, create_by, create_time)"
                            + " VALUES (?, ?, ?, ?, 1, 'system', CURRENT_TIMESTAMP)",
                    IdWorker.getId(), provider, subject, personId);
        } catch (DuplicateKeyException ex) {
            jdbcTemplate.update(
                    "UPDATE " + table
                            + " SET person_id = ?, deleted = NULL, update_time = CURRENT_TIMESTAMP"
                            + " WHERE provider = ? AND subject = ?",
                    personId, provider, subject);
        }
        return new ExternalIdentity(provider, subject, personId);
    }

    @Override
    @Transactional
    public boolean unbindByPerson(String provider, Long personId) {
        if (provider == null || provider.isBlank() || personId == null) {
            throw new IllegalArgumentException("unbindByPerson 参数不能为空");
        }
        int updated = jdbcTemplate.update(
                "UPDATE " + table
                        + " SET deleted = CURRENT_TIMESTAMP, update_time = CURRENT_TIMESTAMP"
                        + " WHERE provider = ? AND person_id = ? AND deleted IS NULL",
                provider,
                personId);
        return updated > 0;
    }

    @Override
    @Transactional
    public void reassignPerson(Long fromPersonId, Long toPersonId) {
        if (fromPersonId == null || toPersonId == null) {
            throw new IllegalArgumentException("reassignPerson 参数不能为空");
        }
        if (fromPersonId.equals(toPersonId)) {
            return;
        }
        jdbcTemplate.update(
                "UPDATE " + table + " SET person_id = ?, update_time = CURRENT_TIMESTAMP"
                        + " WHERE person_id = ? AND deleted IS NULL",
                toPersonId, fromPersonId);
    }
}
