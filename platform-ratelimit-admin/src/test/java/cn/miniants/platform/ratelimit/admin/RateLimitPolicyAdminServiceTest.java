package cn.miniants.platform.ratelimit.admin;

import cn.miniants.platform.core.json.Jsons;
import cn.miniants.platform.ratelimit.RateLimitAlgorithm;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.StoreFailurePolicy;
import cn.miniants.platform.ratelimit.admin.dto.RateLimitBucketsVo;
import cn.miniants.platform.ratelimit.admin.dto.RateLimitPolicySave;
import cn.miniants.platform.ratelimit.admin.dto.RateLimitPolicyVo;
import cn.miniants.platform.ratelimit.admin.dto.RateLimitRuntimeVo;
import cn.miniants.platform.ratelimit.spi.RateLimitBucketSnapshot;
import cn.miniants.platform.ratelimit.spi.RateLimitBucketView;
import cn.miniants.platform.ratelimit.admin.service.RateLimitPolicyAdminService;
import cn.miniants.platform.ratelimit.admin.snapshot.RateLimitPolicySnapshotCodec;
import cn.miniants.platform.ratelimit.admin.store.RateLimitPolicyJdbcRepository;
import cn.miniants.platform.ratelimit.config.RateLimitProperties;
import cn.miniants.platform.ratelimit.metrics.RateLimitMetricsRecorder;
import cn.miniants.platform.ratelimit.policy.CompositeRateLimitPolicyRegistry;
import cn.miniants.platform.ratelimit.redis.snapshot.RateLimitPolicySnapshotPublisher;
import org.flywaydb.core.Flyway;
import org.h2.Driver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitPolicyAdminServiceTest {

    private RateLimitPolicyJdbcRepository repository;
    private RateLimitPolicySnapshotCodec adminCodec;
    private CompositeRateLimitPolicyRegistry registry;
    private RecordingPublisher publisher;
    private RateLimitPolicyAdminService service;
    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:rl_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/ratelimit-migration")
                .table("ratelimit_schema_history")
                .load()
                .migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new RateLimitPolicyJdbcRepository(jdbcTemplate);
        adminCodec = new RateLimitPolicySnapshotCodec(Jsons.mapper());
        registry = new CompositeRateLimitPolicyRegistry(List.of());
        publisher = new RecordingPublisher();
        RateLimitProperties properties = new RateLimitProperties();
        service = new RateLimitPolicyAdminService(
                repository,
                adminCodec,
                new cn.miniants.platform.ratelimit.snapshot.RateLimitPolicySnapshotCodec(Jsons.mapper()),
                publisher,
                registry,
                properties,
                RateLimitMetricsRecorder.NOOP);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test
    void flywayCreatesTables() {
        assertThat(repository.count(null)).isZero();
    }

    @Test
    void runtimeListsYamlPoliciesBeforeAnySave() {
        RateLimitPolicy yaml = RateLimitPolicy.builder()
                .policyCode("auth.login")
                .algorithm(RateLimitAlgorithm.GCRA)
                .limit(20)
                .period(Duration.ofMinutes(1))
                .burst(20)
                .storeFailurePolicy(StoreFailurePolicy.DENY)
                .enabled(true)
                .build();
        CompositeRateLimitPolicyRegistry withYaml = new CompositeRateLimitPolicyRegistry(List.of(yaml));
        RateLimitPolicyAdminService viewing = new RateLimitPolicyAdminService(
                repository,
                adminCodec,
                new cn.miniants.platform.ratelimit.snapshot.RateLimitPolicySnapshotCodec(Jsons.mapper()),
                publisher,
                withYaml,
                new RateLimitProperties(),
                RateLimitMetricsRecorder.NOOP);

        RateLimitRuntimeVo vo = viewing.runtime();

        assertThat(vo.getAvailablePolicies()).containsExactly("auth.login");
        assertThat(vo.getEffectivePolicies()).singleElement().satisfies(item -> {
            assertThat(item.getPolicyCode()).isEqualTo("auth.login");
            assertThat(item.getLimitCount()).isEqualTo(20L);
            assertThat(item.getPeriodMs()).isEqualTo(60_000L);
            assertThat(item.getSource()).isEqualTo("yaml");
        });
    }

    @Test
    void bucketsMapsInspectorSnapshot() {
        RateLimitPolicyAdminService viewing = new RateLimitPolicyAdminService(
                repository,
                adminCodec,
                new cn.miniants.platform.ratelimit.snapshot.RateLimitPolicySnapshotCodec(Jsons.mapper()),
                publisher,
                registry,
                new RateLimitProperties(),
                RateLimitMetricsRecorder.NOOP,
                max -> new RateLimitBucketSnapshot(
                        java.time.Instant.parse("2026-08-29T06:00:00Z"),
                        "redis",
                        false,
                        List.of(new RateLimitBucketView("auth.login", "127.0.0.1", "GCRA", 20, 19, 0, 1_000L))));

        RateLimitBucketsVo vo = viewing.buckets(50);

        assertThat(vo.getBackend()).isEqualTo("redis");
        assertThat(vo.getBuckets()).singleElement().satisfies(item -> {
            assertThat(item.getPolicyCode()).isEqualTo("auth.login");
            assertThat(item.getSubject()).isEqualTo("127.0.0.1");
            assertThat(item.getRemaining()).isEqualTo(19L);
            assertThat(item.getLimitCount()).isEqualTo(20L);
        });
    }

    @Test
    void saveAndOptimisticLockConflict() {
        RateLimitPolicyVo created = tx.execute(status -> service.save(newSave("auth.login", null)));
        assertThat(created.getVersion()).isEqualTo(1L);
        assertThat(publisher.revisions).hasSize(1);
        assertThat(registry.revision()).isEqualTo(publisher.revisions.get(0));
        assertThat(registry.require("auth.login").limit()).isEqualTo(10L);

        RateLimitPolicySave stale = newSave("auth.login", created.getId());
        stale.setVersion(1L);
        stale.setLimitCount(20L);

        tx.execute(status -> {
            RateLimitPolicySave first = newSave("auth.login", created.getId());
            first.setVersion(1L);
            first.setLimitCount(30L);
            return service.save(first);
        });

        assertThatThrownBy(() -> tx.execute(status -> service.save(stale)))
                .isInstanceOf(RateLimitVersionConflictException.class);
    }

    @Test
    void afterCommitPublishesSnapshot() {
        publisher.clear();
        RateLimitPolicyVo created = tx.execute(status -> service.save(newSave("sms.send", null)));
        assertThat(publisher.revisions).isNotEmpty();
        assertThat(publisher.lastJson.get()).contains("sms.send");
        assertThat(created.getPolicyCode()).isEqualTo("sms.send");
        assertThat(registry.require("sms.send").limit()).isEqualTo(10L);
    }

    @Test
    void enableDisableConflictAndRollback() {
        RateLimitPolicyVo created = tx.execute(status -> service.save(newSave("auth.login", null)));
        assertThat(created.getEnabled()).isTrue();

        RateLimitPolicyVo disabled = tx.execute(status -> service.disable(created.getId()));
        assertThat(disabled.getEnabled()).isFalse();
        assertThat(disabled.getVersion()).isEqualTo(created.getVersion() + 1L);

        RateLimitPolicyVo enabled = tx.execute(status -> service.enable(created.getId()));
        assertThat(enabled.getEnabled()).isTrue();

        long firstRevision = publisher.revisions.get(0);
        assertThat(service.listRevisions(10).get(0).getSnapshotJson()).isNull();
        assertThat(service.getRevision(firstRevision).getSnapshotJson()).isNotBlank();

        long rolled = tx.execute(status -> service.rollback(firstRevision));
        assertThat(rolled).isGreaterThan(firstRevision);
        assertThat(service.page(1L, 20L, "auth.login").getRecords()).isNotEmpty();
    }

    @Test
    void publishFailureDoesNotRollBackAndCanRetry() {
        publisher.failOnce = true;
        tx.execute(status -> service.save(newSave("retry.me", null)));
        assertThat(registry.require("retry.me").limit()).isEqualTo(10L);
        assertThat(registry.publishFailed()).isTrue();
        assertThat(repository.latestRevision()).isPresent();

        publisher.failOnce = false;
        service.retryPublishLatest();
        assertThat(registry.publishFailed()).isFalse();
        assertThat(publisher.revisions).isNotEmpty();
    }

    @Test
    void registryRefreshFromRuntimeSnapshot() {
        tx.execute(status -> service.save(newSave("api.call", null)));
        String snapshot = publisher.lastJson.get();
        assertThat(snapshot).isNotBlank();

        CompositeRateLimitPolicyRegistry other = new CompositeRateLimitPolicyRegistry(List.of());
        var codec = new cn.miniants.platform.ratelimit.snapshot.RateLimitPolicySnapshotCodec(Jsons.mapper());
        var decoded = codec.decode(snapshot);
        assertThat(other.applyDynamicRevision(codec.toMap(decoded), decoded.revision(), java.time.Instant.now())
                .name()).isEqualTo("APPLIED");
        assertThat(other.require("api.call").limit()).isEqualTo(10L);
        assertThat(other.revision()).isGreaterThan(0L);
    }

    private static RateLimitPolicySave newSave(String code, Long id) {
        RateLimitPolicySave body = new RateLimitPolicySave();
        body.setId(id);
        body.setPolicyCode(code);
        body.setAlgorithm("GCRA");
        body.setLimitCount(10L);
        body.setPeriodMs(60_000L);
        body.setBurst(10L);
        body.setStoreFailurePolicy("DENY");
        body.setEnabled(true);
        return body;
    }

    private static final class RecordingPublisher implements RateLimitPolicySnapshotPublisher {
        private final List<Long> revisions = new ArrayList<>();
        private final AtomicReference<String> lastJson = new AtomicReference<>();
        private boolean failOnce;

        @Override
        public int publish(long revision, String snapshotJson) {
            if (failOnce) {
                failOnce = false;
                throw new IllegalStateException("redis down");
            }
            revisions.add(revision);
            lastJson.set(snapshotJson);
            return 1;
        }

        void clear() {
            revisions.clear();
            lastJson.set(null);
        }
    }
}
