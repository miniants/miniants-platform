package cn.miniants.platform.admin.person;

import cn.miniants.platform.data.PlatformDataProperties;
import cn.miniants.platform.security.account.UserAccount;
import cn.miniants.platform.security.person.PrincipalDirectory;
import cn.miniants.platform.security.person.PrincipalSummary;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

/**
 * JDBC {@link PrincipalDirectory}：按 {@code person_id} 列启用账号，并校验 designated。
 */
public class JdbcPrincipalDirectory implements PrincipalDirectory {

    private final JdbcTemplate jdbcTemplate;
    private final String userTable;
    private final String userRoleTable;
    private final String roleTable;

    public JdbcPrincipalDirectory(JdbcTemplate jdbcTemplate, PlatformDataProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        String prefix = properties.jdbcTablePrefix();
        this.userTable = prefix + "user";
        this.userRoleTable = prefix + "user_role";
        this.roleTable = prefix + "role";
    }

    @Override
    public List<PrincipalSummary> listByPersonId(Long personId) {
        if (personId == null) {
            return List.of();
        }
        return jdbcTemplate.query(
                "SELECT id, username, display_name FROM " + userTable
                        + " WHERE person_id = ? AND status = 1 AND deleted IS NULL ORDER BY id",
                (rs, rowNum) -> new PrincipalSummary(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("display_name")),
                personId);
    }

    @Override
    public Optional<UserAccount> resolveDesignated(Long personId, String designatedUsername) {
        if (personId == null || designatedUsername == null || designatedUsername.isBlank()) {
            return Optional.empty();
        }
        try {
            UserAccount user = jdbcTemplate.queryForObject(
                    "SELECT id, username, password, display_name, status, sys_admin, person_id FROM "
                            + userTable
                            + " WHERE username = ? AND person_id = ? AND status = 1 AND deleted IS NULL",
                    (rs, rowNum) -> new UserAccount(
                            rs.getLong("id"),
                            rs.getString("username"),
                            rs.getString("password"),
                            rs.getString("display_name"),
                            rs.getInt("status") == 1,
                            rs.getInt("sys_admin") == 1,
                            List.of(),
                            (Long) rs.getObject("person_id")),
                    designatedUsername,
                    personId);
            if (user == null) {
                return Optional.empty();
            }
            List<Long> roleIds = jdbcTemplate.query(
                    "SELECT ur.role_id FROM " + userRoleTable + " ur INNER JOIN " + roleTable
                            + " r ON r.id = ur.role_id WHERE ur.user_id = ? AND r.deleted IS NULL AND r.status = 1",
                    (rs, rowNum) -> rs.getLong("role_id"),
                    user.id());
            return Optional.of(new UserAccount(
                    user.id(), user.username(), user.passwordHash(), user.displayName(),
                    user.enabled(), user.sysAdmin(), roleIds, user.personId()));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }
}
