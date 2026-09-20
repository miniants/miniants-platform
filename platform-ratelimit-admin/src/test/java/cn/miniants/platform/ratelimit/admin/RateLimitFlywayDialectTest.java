package cn.miniants.platform.ratelimit.admin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFlywayDialectTest {

    @Test
    void mysql8MigrationUsesLongTextNotClob() throws IOException {
        Path dir = Path.of("src/main/resources/db/ratelimit-migration");
        String v1 = Files.readString(dir.resolve("V1__create_sys_rate_limit_policy.sql"), StandardCharsets.UTF_8);
        String v2 = Files.readString(dir.resolve("V2__ensure_sys_rate_limit_policy.sql"), StandardCharsets.UTF_8);
        assertThat(v1).contains("LONGTEXT");
        assertThat(v2).contains("LONGTEXT");
        assertThat(v1.toUpperCase()).doesNotContain("CLOB");
        assertThat(v2.toUpperCase()).doesNotContain("CLOB");
    }
}
