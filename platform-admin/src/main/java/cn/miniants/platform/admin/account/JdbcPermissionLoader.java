package cn.miniants.platform.admin.account;

import cn.miniants.platform.data.PlatformDataProperties;
import cn.miniants.platform.security.PermissionLoader;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;
import java.util.stream.Collectors;

public class JdbcPermissionLoader implements PermissionLoader {

    private final JdbcTemplate jdbcTemplate;
    private final String userRoleTable;
    private final String roleTable;
    private final String resourceTable;
    private final String roleResourceTable;

    public JdbcPermissionLoader(JdbcTemplate jdbcTemplate, PlatformDataProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        String prefix = properties.jdbcTablePrefix();
        this.userRoleTable = prefix + "user_role";
        this.roleTable = prefix + "role";
        this.resourceTable = prefix + "resource";
        this.roleResourceTable = prefix + "role_resource";
    }

    @Override
    public Set<String> load(Long userId, Collection<Long> roleIds) {
        List<Long> ids = distinct(roleIds);
        if (ids.isEmpty() && userId != null) {
            ids = jdbcTemplate.query(
                    "SELECT ur.role_id FROM " + userRoleTable + " ur INNER JOIN " + roleTable
                            + " r ON r.id = ur.role_id WHERE ur.user_id = ? AND r.deleted IS NULL AND r.status = 1",
                    (rs, rowNum) -> rs.getLong("role_id"),
                    userId);
        }
        if (ids.isEmpty()) {
            return Set.of();
        }
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        List<String> codes = jdbcTemplate.query(
                "SELECT DISTINCT res.code FROM " + roleResourceTable + " rr"
                        + " INNER JOIN " + resourceTable + " res ON res.id = rr.resource_id"
                        + " INNER JOIN " + roleTable + " r ON r.id = rr.role_id"
                        + " WHERE rr.role_id IN (" + placeholders + ")"
                        + " AND res.deleted IS NULL AND res.status = 1"
                        + " AND r.deleted IS NULL AND r.status = 1",
                (rs, rowNum) -> rs.getString("code"),
                ids.toArray());
        return new LinkedHashSet<>(codes);
    }

    private static List<Long> distinct(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (Long id : roleIds) {
            if (id != null && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }
}
