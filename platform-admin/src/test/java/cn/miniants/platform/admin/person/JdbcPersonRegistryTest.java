package cn.miniants.platform.admin.person;

import cn.miniants.platform.data.PlatformDataProperties;
import org.flywaydb.core.Flyway;
import org.h2.Driver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcPersonRegistryTest {

    private JdbcTemplate jdbcTemplate;
    private JdbcPersonRegistry registry;
    private TransactionTemplate tx;

    @BeforeEach
    void migrate() {
        DataSource dataSource = new SimpleDriverDataSource(new Driver(),
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
        PlatformDataProperties properties = new PlatformDataProperties();
        registry = new JdbcPersonRegistry(jdbcTemplate, properties);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test
    void verifyOrCreateIsIdempotentForSameDigest() {
        var first = registry.verifyOrCreate("张三", "id_card", "digest-a", null);
        var second = registry.verifyOrCreate("张三", "id_card", "digest-a", null);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_person WHERE id_lookup_digest = 'digest-a' AND deleted IS NULL",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentVerifyOrCreateKeepsSingleRow() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>(threads);
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return tx.execute(status -> registry.verifyOrCreate(
                            "并发", "id_card", "digest-concurrent", null).id());
                }));
            }
            start.countDown();
            List<Long> ids = new ArrayList<>(threads);
            for (Future<Long> future : futures) {
                ids.add(future.get(10, TimeUnit.SECONDS));
            }
            assertThat(ids).doesNotContainNull();
            assertThat(ids.stream().distinct()).hasSize(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_person WHERE id_lookup_digest = 'digest-concurrent' AND deleted IS NULL",
                    Integer.class)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void blankDigestIsRejected() {
        assertThatThrownBy(() -> registry.verifyOrCreate("张三", "id_card", "  ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("摘要");
    }
}
