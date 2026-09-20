package cn.miniants.platform.admin.client;

import cn.miniants.platform.data.PlatformDataProperties;
import cn.miniants.platform.security.client.OauthClientDescriptor;
import cn.miniants.platform.security.client.OauthClientLookup;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class JdbcOauthClientLookup implements OauthClientLookup {

    private final JdbcTemplate jdbcTemplate;
    private final String table;

    public JdbcOauthClientLookup(JdbcTemplate jdbcTemplate, PlatformDataProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        String prefix = properties.jdbcTablePrefix();
        this.table = prefix + "oauth_client";
    }

    @Override
    public Optional<OauthClientDescriptor> findByClientId(String clientId) {
        return find("SELECT * FROM " + table + " WHERE client_id = ? AND deleted IS NULL", clientId);
    }

    @Override
    public Optional<OauthClientDescriptor> findById(String id) {
        return find("SELECT * FROM " + table + " WHERE id = ? AND deleted IS NULL", id);
    }

    private Optional<OauthClientDescriptor> find(String sql, Object arg) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, mapper(), arg));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private static RowMapper<OauthClientDescriptor> mapper() {
        return (rs, rowNum) -> new OauthClientDescriptor(
                String.valueOf(rs.getLong("id")),
                rs.getString("client_id"),
                rs.getString("client_secret"),
                rs.getString("client_name"),
                split(rs.getString("grant_types")),
                split(rs.getString("scopes")),
                rs.getInt("access_token_ttl"),
                rs.getInt("refresh_token_ttl"),
                rs.getInt("status") == 1);
    }

    private static List<String> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
    }
}
