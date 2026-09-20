package cn.miniants.platform.admin.account;

import cn.miniants.platform.data.PlatformDataProperties;
import cn.miniants.platform.security.account.UserAccount;
import cn.miniants.platform.security.account.UserAccountService;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

public class JdbcUserAccountService implements UserAccountService {

    private final JdbcTemplate jdbcTemplate;
    private final String userTable;
    private final String userRoleTable;
    private final String roleTable;

    public JdbcUserAccountService(JdbcTemplate jdbcTemplate, PlatformDataProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        String prefix = properties.jdbcTablePrefix();
        this.userTable = prefix + "user";
        this.userRoleTable = prefix + "user_role";
        this.roleTable = prefix + "role";
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        try {
            UserAccount user = jdbcTemplate.queryForObject(
                    "SELECT id, username, password, display_name, status, sys_admin, person_id FROM "
                            + userTable + " WHERE username = ? AND deleted IS NULL",
                    (rs, rowNum) -> mapUser(rs),
                    username);
            return Optional.ofNullable(withRoles(user));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<UserAccount> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        try {
            UserAccount user = jdbcTemplate.queryForObject(
                    "SELECT id, username, password, display_name, status, sys_admin, person_id FROM "
                            + userTable + " WHERE id = ? AND deleted IS NULL",
                    (rs, rowNum) -> mapUser(rs),
                    id);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<UserAccount> findEnabledByPersonId(Long personId) {
        if (personId == null) {
            return List.of();
        }
        List<UserAccount> users = jdbcTemplate.query(
                "SELECT id, username, password, display_name, status, sys_admin, person_id FROM "
                        + userTable
                        + " WHERE person_id = ? AND status = 1 AND deleted IS NULL ORDER BY id",
                (rs, rowNum) -> mapUser(rs),
                personId);
        return users.stream().map(this::withRoles).toList();
    }

    @Override
    public void updatePersonId(Long userId, Long personId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        int updated = jdbcTemplate.update(
                "UPDATE " + userTable + " SET person_id = ? WHERE id = ? AND deleted IS NULL",
                personId,
                userId);
        if (updated == 0) {
            throw new IllegalArgumentException("用户不存在");
        }
    }

    @Override
    public void updatePasswordHash(Long id, String passwordHash) {
        if (id == null || passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("用户不存在");
        }
        int updated = jdbcTemplate.update(
                "UPDATE " + userTable + " SET password = ? WHERE id = ? AND deleted IS NULL",
                passwordHash,
                id);
        if (updated == 0) {
            throw new IllegalArgumentException("用户不存在");
        }
    }

    private UserAccount withRoles(UserAccount user) {
        if (user == null) {
            return null;
        }
        List<Long> roleIds = jdbcTemplate.query(
                "SELECT ur.role_id FROM " + userRoleTable + " ur INNER JOIN " + roleTable
                        + " r ON r.id = ur.role_id WHERE ur.user_id = ? AND r.deleted IS NULL AND r.status = 1",
                (rs, rowNum) -> rs.getLong("role_id"),
                user.id());
        return new UserAccount(
                user.id(), user.username(), user.passwordHash(), user.displayName(),
                user.enabled(), user.sysAdmin(), roleIds, user.personId());
    }

    private static UserAccount mapUser(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new UserAccount(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("password"),
                rs.getString("display_name"),
                rs.getInt("status") == 1,
                rs.getInt("sys_admin") == 1,
                List.of(),
                (Long) rs.getObject("person_id"));
    }
}
