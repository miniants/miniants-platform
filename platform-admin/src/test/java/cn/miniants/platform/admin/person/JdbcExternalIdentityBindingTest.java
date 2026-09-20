package cn.miniants.platform.admin.person;

import cn.miniants.platform.data.PlatformDataProperties;
import cn.miniants.platform.security.identity.ExternalIdentity;
import org.flywaydb.core.Flyway;
import org.h2.Driver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcExternalIdentityBindingTest {

    private JdbcTemplate jdbcTemplate;
    private JdbcExternalIdentityBinding binding;

    @BeforeEach
    void migrate() {
        DataSource dataSource = new SimpleDriverDataSource(new Driver(),
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
        binding = new JdbcExternalIdentityBinding(jdbcTemplate, new PlatformDataProperties());
    }

    @Test
    void bindAndFindRoundTrip() {
        binding.bind("wx", "openid-1", 100L);

        ExternalIdentity found = binding.find("wx", "openid-1").orElseThrow();
        assertThat(found.personId()).isEqualTo(100L);
        assertThat(binding.findByPerson("wx", 100L)).contains(found);
    }

    @Test
    void rebindSameSubjectUpdatesPerson() {
        binding.bind("wx", "openid-1", 100L);
        binding.bind("wx", "openid-1", 200L);

        ExternalIdentity found = binding.find("wx", "openid-1").orElseThrow();
        assertThat(found.personId()).isEqualTo(200L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_external_identity WHERE provider = 'wx' AND subject = 'openid-1'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void unbindByPersonSoftDeletesAndRebindRevives() {
        binding.bind("wx", "openid-1", 100L);
        assertThat(binding.unbindByPerson("wx", 100L)).isTrue();
        assertThat(binding.find("wx", "openid-1")).isEmpty();
        assertThat(binding.findByPerson("wx", 100L)).isEmpty();
        assertThat(binding.unbindByPerson("wx", 100L)).isFalse();

        binding.bind("wx", "openid-1", 100L);
        assertThat(binding.find("wx", "openid-1")).isPresent();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_external_identity WHERE provider = 'wx' AND subject = 'openid-1'",
                Integer.class)).isEqualTo(1);
    }
}
