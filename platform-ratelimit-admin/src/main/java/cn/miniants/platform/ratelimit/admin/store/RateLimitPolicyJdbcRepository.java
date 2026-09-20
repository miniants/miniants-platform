package cn.miniants.platform.ratelimit.admin.store;

import cn.miniants.platform.ratelimit.admin.dto.RateLimitPolicyRevisionVo;
import cn.miniants.platform.ratelimit.admin.dto.RateLimitPolicyVo;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 限流策略 JDBC 存储。
 */
public class RateLimitPolicyJdbcRepository {

    private static final RowMapper<RateLimitPolicyVo> POLICY_MAPPER = (rs, rowNum) -> {
        RateLimitPolicyVo vo = new RateLimitPolicyVo();
        vo.setId(rs.getLong("id"));
        vo.setPolicyCode(rs.getString("policy_code"));
        vo.setAlgorithm(rs.getString("algorithm"));
        vo.setLimitCount(rs.getLong("limit_count"));
        vo.setPeriodMs(rs.getLong("period_ms"));
        vo.setBurst(rs.getLong("burst"));
        vo.setStoreFailurePolicy(rs.getString("store_failure_policy"));
        vo.setEnabled(rs.getInt("enabled") == 1);
        vo.setVersion(rs.getLong("version"));
        vo.setRemark(rs.getString("remark"));
        vo.setCreateBy(rs.getString("create_by"));
        Timestamp createTime = rs.getTimestamp("create_time");
        vo.setCreateTime(createTime == null ? null : createTime.toLocalDateTime());
        vo.setUpdateBy(rs.getString("update_by"));
        Timestamp updateTime = rs.getTimestamp("update_time");
        vo.setUpdateTime(updateTime == null ? null : updateTime.toLocalDateTime());
        return vo;
    };

    private static final RowMapper<RateLimitPolicyRevisionVo> REVISION_MAPPER = (rs, rowNum) -> {
        RateLimitPolicyRevisionVo vo = new RateLimitPolicyRevisionVo();
        vo.setRevision(rs.getLong("revision"));
        vo.setSnapshotJson(rs.getString("snapshot_json"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        vo.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
        vo.setCreatedBy(rs.getString("created_by"));
        return vo;
    };

    private final JdbcTemplate jdbcTemplate;

    public RateLimitPolicyJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long count(String policyCode) {
        if (policyCode == null || policyCode.isBlank()) {
            Long total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM sys_rate_limit_policy", Long.class);
            return total == null ? 0L : total;
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_rate_limit_policy WHERE policy_code LIKE ?",
                Long.class,
                "%" + policyCode.trim() + "%");
        return total == null ? 0L : total;
    }

    public List<RateLimitPolicyVo> page(String policyCode, long offset, long limit) {
        if (policyCode == null || policyCode.isBlank()) {
            return jdbcTemplate.query(
                    "SELECT * FROM sys_rate_limit_policy ORDER BY update_time DESC, id DESC LIMIT ? OFFSET ?",
                    POLICY_MAPPER,
                    limit,
                    offset);
        }
        return jdbcTemplate.query(
                "SELECT * FROM sys_rate_limit_policy WHERE policy_code LIKE ? ORDER BY update_time DESC, id DESC LIMIT ? OFFSET ?",
                POLICY_MAPPER,
                "%" + policyCode.trim() + "%",
                limit,
                offset);
    }

    public Optional<RateLimitPolicyVo> findById(long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT * FROM sys_rate_limit_policy WHERE id = ?",
                    POLICY_MAPPER,
                    id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<RateLimitPolicyVo> findByCode(String policyCode) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT * FROM sys_rate_limit_policy WHERE policy_code = ?",
                    POLICY_MAPPER,
                    policyCode));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public List<RateLimitPolicyVo> findAll() {
        return jdbcTemplate.query(
                "SELECT * FROM sys_rate_limit_policy ORDER BY policy_code ASC",
                POLICY_MAPPER);
    }

    public void insert(RateLimitPolicyVo row) {
        jdbcTemplate.update(
                """
                INSERT INTO sys_rate_limit_policy
                (id, policy_code, algorithm, limit_count, period_ms, burst, store_failure_policy,
                 enabled, version, remark, create_by, create_time, update_by, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                row.getId(),
                row.getPolicyCode(),
                row.getAlgorithm(),
                row.getLimitCount(),
                row.getPeriodMs(),
                row.getBurst(),
                row.getStoreFailurePolicy(),
                Boolean.TRUE.equals(row.getEnabled()) ? 1 : 0,
                row.getVersion(),
                row.getRemark(),
                row.getCreateBy(),
                timestamp(row.getCreateTime()),
                row.getUpdateBy(),
                timestamp(row.getUpdateTime()));
    }

    /**
     * @return 更新行数；0 表示乐观锁冲突或不存在
     */
    public int updateOptimistic(RateLimitPolicyVo row, long expectedVersion) {
        return jdbcTemplate.update(
                """
                UPDATE sys_rate_limit_policy
                SET policy_code = ?, algorithm = ?, limit_count = ?, period_ms = ?, burst = ?,
                    store_failure_policy = ?, enabled = ?, version = ?, remark = ?,
                    update_by = ?, update_time = ?
                WHERE id = ? AND version = ?
                """,
                row.getPolicyCode(),
                row.getAlgorithm(),
                row.getLimitCount(),
                row.getPeriodMs(),
                row.getBurst(),
                row.getStoreFailurePolicy(),
                Boolean.TRUE.equals(row.getEnabled()) ? 1 : 0,
                row.getVersion(),
                row.getRemark(),
                row.getUpdateBy(),
                timestamp(row.getUpdateTime()),
                row.getId(),
                expectedVersion);
    }

    public int updateEnabled(long id, boolean enabled, long expectedVersion, long newVersion,
            String updateBy, LocalDateTime updateTime) {
        return jdbcTemplate.update(
                """
                UPDATE sys_rate_limit_policy
                SET enabled = ?, version = ?, update_by = ?, update_time = ?
                WHERE id = ? AND version = ?
                """,
                enabled ? 1 : 0,
                newVersion,
                updateBy,
                timestamp(updateTime),
                id,
                expectedVersion);
    }

    public void deleteAll() {
        jdbcTemplate.update("DELETE FROM sys_rate_limit_policy");
    }

    public void replaceAll(List<RateLimitPolicyVo> rows) {
        deleteAll();
        for (RateLimitPolicyVo row : rows) {
            insert(row);
        }
    }

    public long insertRevision(String snapshotJson, String createdBy, LocalDateTime createdAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO sys_rate_limit_policy_revision (snapshot_json, created_at, created_by) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, snapshotJson);
            ps.setTimestamp(2, timestamp(createdAt));
            ps.setString(3, createdBy);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("写入策略修订号失败");
        }
        return key.longValue();
    }

    public List<RateLimitPolicyRevisionVo> listRevisions(int limit) {
        return listRevisionSummaries(limit);
    }

    public List<RateLimitPolicyRevisionVo> listRevisionSummaries(int limit) {
        int size = Math.max(1, Math.min(limit, 200));
        return jdbcTemplate.query(
                "SELECT revision, created_at, created_by FROM sys_rate_limit_policy_revision ORDER BY revision DESC LIMIT ?",
                (rs, rowNum) -> {
                    RateLimitPolicyRevisionVo vo = new RateLimitPolicyRevisionVo();
                    vo.setRevision(rs.getLong("revision"));
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    vo.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
                    vo.setCreatedBy(rs.getString("created_by"));
                    return vo;
                },
                size);
    }

    public Optional<RateLimitPolicyRevisionVo> findRevision(long revision) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT revision, snapshot_json, created_at, created_by FROM sys_rate_limit_policy_revision WHERE revision = ?",
                    REVISION_MAPPER,
                    revision));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<Long> latestRevision() {
        List<Long> rows = jdbcTemplate.query(
                "SELECT revision FROM sys_rate_limit_policy_revision ORDER BY revision DESC LIMIT 1",
                (rs, rowNum) -> rs.getLong(1));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public void updateRevisionSnapshot(long revision, String snapshotJson) {
        jdbcTemplate.update(
                "UPDATE sys_rate_limit_policy_revision SET snapshot_json = ? WHERE revision = ?",
                snapshotJson,
                revision);
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }
}
