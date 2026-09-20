package cn.miniants.platform.admin.person;

import cn.miniants.platform.data.PlatformDataProperties;
import cn.miniants.platform.security.account.UserAccount;
import org.flywaydb.core.Flyway;
import org.h2.Driver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcPrincipalDirectoryTest {

    private JdbcTemplate jdbcTemplate;
    private JdbcPrincipalDirectory directory;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void migrate() {
        DataSource dataSource = new SimpleDriverDataSource(new Driver(),
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
        directory = new JdbcPrincipalDirectory(jdbcTemplate, new PlatformDataProperties());
        seedPersonAccounts(501L, "alice", "bob");
    }

    @Test
    void listsEnabledAccountsForPerson() {
        assertThat(directory.listByPersonId(501L))
                .hasSize(2)
                .extracting(summary -> summary.username())
                .containsExactly("alice", "bob");
    }

    @Test
    void resolveDesignatedRequiresSamePerson() {
        UserAccount alice = directory.resolveDesignated(501L, "alice").orElseThrow();
        assertThat(alice.username()).isEqualTo("alice");
        assertThat(directory.resolveDesignated(501L, "mallory")).isEmpty();
    }

    private void seedPersonAccounts(long personId, String... usernames) {
        jdbcTemplate.update(
                "INSERT INTO sys_person (id, real_name, id_doc_type, version, create_by, create_time)"
                        + " VALUES (?, '测试', 'id_card', 1, 'system', CURRENT_TIMESTAMP)",
                personId);
        long roleId = 900L;
        jdbcTemplate.update(
                "INSERT INTO sys_role (id, code, name, status, version, create_by, create_time)"
                        + " VALUES (?, 'operator', '操作员', 1, 1, 'system', CURRENT_TIMESTAMP)",
                roleId);
        long userSeq = 1000L;
        for (String username : usernames) {
            jdbcTemplate.update(
                    "INSERT INTO sys_user (id, username, password, display_name, status, sys_admin, person_id, version, create_by, create_time)"
                            + " VALUES (?, ?, ?, ?, 1, 0, ?, 1, 'system', CURRENT_TIMESTAMP)",
                    userSeq,
                    username,
                    encoder.encode("secret"),
                    username,
                    personId);
            jdbcTemplate.update(
                    "INSERT INTO sys_user_role (id, user_id, role_id) VALUES (?, ?, ?)",
                    userSeq + 100, userSeq, roleId);
            userSeq++;
        }
    }
}
