package cn.miniants.platform.ratelimit.admin.service;

import cn.miniants.platform.core.error.PlatformException;
import cn.miniants.platform.ratelimit.RateLimitAlgorithm;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.StoreFailurePolicy;
import cn.miniants.platform.ratelimit.admin.RateLimitVersionConflictException;
import cn.miniants.platform.ratelimit.admin.dto.PageResult;
import cn.miniants.platform.ratelimit.admin.dto.RateLimitBucketVo;
import cn.miniants.platform.ratelimit.admin.dto.RateLimitBucketsVo;
import cn.miniants.platform.ratelimit.admin.dto.RateLimitEffectivePolicyVo;
import cn.miniants.platform.ratelimit.admin.dto.RateLimitPolicyRevisionVo;
import cn.miniants.platform.ratelimit.admin.dto.RateLimitPolicySave;
import cn.miniants.platform.ratelimit.admin.dto.RateLimitPolicyVo;
import cn.miniants.platform.ratelimit.admin.dto.RateLimitRuntimeVo;
import cn.miniants.platform.ratelimit.admin.snapshot.RateLimitPolicySnapshotCodec;
import cn.miniants.platform.ratelimit.admin.store.RateLimitPolicyJdbcRepository;
import cn.miniants.platform.ratelimit.config.RateLimitProperties;
import cn.miniants.platform.ratelimit.metrics.RateLimitMetricsRecorder;
import cn.miniants.platform.ratelimit.policy.CompositeRateLimitPolicyRegistry;
import cn.miniants.platform.ratelimit.spi.RateLimitBucketInspector;
import cn.miniants.platform.ratelimit.spi.RateLimitBucketSnapshot;
import cn.miniants.platform.ratelimit.spi.RateLimitBucketView;
import cn.miniants.platform.ratelimit.redis.snapshot.RateLimitPolicySnapshotPublisher;
import cn.miniants.platform.security.CurrentUser;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 限流策略管理：持久化、乐观锁、提交后先更新本机 LKG 再尝试发布。
 */
public class RateLimitPolicyAdminService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitPolicyAdminService.class);

    private final RateLimitPolicyJdbcRepository repository;
    private final RateLimitPolicySnapshotCodec adminCodec;
    private final cn.miniants.platform.ratelimit.snapshot.RateLimitPolicySnapshotCodec runtimeCodec;
    private final RateLimitPolicySnapshotPublisher publisher;
    private final CompositeRateLimitPolicyRegistry registry;
    private final RateLimitProperties rateLimitProperties;
    private static final int DEFAULT_BUCKET_LIMIT = 500;

    private final RateLimitMetricsRecorder metrics;
    private final RateLimitBucketInspector bucketInspector;

    public RateLimitPolicyAdminService(
            RateLimitPolicyJdbcRepository repository,
            RateLimitPolicySnapshotCodec adminCodec,
            cn.miniants.platform.ratelimit.snapshot.RateLimitPolicySnapshotCodec runtimeCodec,
            RateLimitPolicySnapshotPublisher publisher,
            CompositeRateLimitPolicyRegistry registry,
            RateLimitProperties rateLimitProperties,
            RateLimitMetricsRecorder metrics) {
        this(repository, adminCodec, runtimeCodec, publisher, registry, rateLimitProperties, metrics, null);
    }

    public RateLimitPolicyAdminService(
            RateLimitPolicyJdbcRepository repository,
            RateLimitPolicySnapshotCodec adminCodec,
            cn.miniants.platform.ratelimit.snapshot.RateLimitPolicySnapshotCodec runtimeCodec,
            RateLimitPolicySnapshotPublisher publisher,
            CompositeRateLimitPolicyRegistry registry,
            RateLimitProperties rateLimitProperties,
            RateLimitMetricsRecorder metrics,
            RateLimitBucketInspector bucketInspector) {
        this.repository = repository;
        this.adminCodec = adminCodec;
        this.runtimeCodec = runtimeCodec;
        this.publisher = publisher;
        this.registry = registry;
        this.rateLimitProperties = rateLimitProperties;
        this.metrics = metrics == null ? RateLimitMetricsRecorder.NOOP : metrics;
        this.bucketInspector = bucketInspector;
    }

    public PageResult<RateLimitPolicyVo> page(Long current, Long size, String policyCode) {
        long page = current == null || current < 1 ? 1L : current;
        long pageSize = size == null || size < 1 ? 20L : Math.min(size, 200L);
        try {
            long total = repository.count(policyCode);
            List<RateLimitPolicyVo> records = total == 0
                    ? List.of()
                    : repository.page(policyCode, (page - 1) * pageSize, pageSize);
            return PageResult.of(records, total, page, pageSize);
        } catch (BadSqlGrammarException ex) {
            throw new PlatformException("限流策略表不存在或结构不匹配，请确认独立 Flyway 已建表", ex);
        }
    }

    public RateLimitPolicyVo get(Long id) {
        if (id == null) {
            throw new PlatformException("策略不存在");
        }
        return repository.findById(id).orElseThrow(() -> new PlatformException("策略不存在"));
    }

    @Transactional
    public RateLimitPolicyVo save(RateLimitPolicySave body) {
        if (body == null) {
            throw new PlatformException("请求参数不正确");
        }
        return body.getId() == null ? create(body) : update(body);
    }

    @Transactional
    public RateLimitPolicyVo enable(Long id) {
        return setEnabled(id, true);
    }

    @Transactional
    public RateLimitPolicyVo disable(Long id) {
        return setEnabled(id, false);
    }

    @Transactional
    public long republish() {
        return publishSnapshot(actor());
    }

    public List<RateLimitPolicyRevisionVo> listRevisions(Integer limit) {
        return repository.listRevisionSummaries(limit == null ? 50 : limit);
    }

    public RateLimitPolicyRevisionVo getRevision(Long revision) {
        if (revision == null) {
            throw new PlatformException("修订号不能为空");
        }
        return repository.findRevision(revision).orElseThrow(() -> new PlatformException("修订不存在"));
    }

    public Map<String, Object> revisionDiff(Long fromRevision, Long toRevision) {
        RateLimitPolicyRevisionVo from = getRevision(fromRevision);
        RateLimitPolicyRevisionVo to = getRevision(toRevision);
        Map<String, RateLimitPolicyVo> fromMap = indexByCode(adminCodec.decode(from.getSnapshotJson()).policies());
        Map<String, RateLimitPolicyVo> toMap = indexByCode(adminCodec.decode(to.getSnapshotJson()).policies());
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        for (String code : toMap.keySet()) {
            if (!fromMap.containsKey(code)) {
                added.add(code);
            } else if (!sameRuntime(fromMap.get(code), toMap.get(code))) {
                changed.add(code);
            }
        }
        for (String code : fromMap.keySet()) {
            if (!toMap.containsKey(code)) {
                removed.add(code);
            }
        }
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("from", fromRevision);
        diff.put("to", toRevision);
        diff.put("added", added);
        diff.put("removed", removed);
        diff.put("changed", changed);
        return diff;
    }

    @Transactional
    public long rollback(Long revision) {
        RateLimitPolicyRevisionVo history = getRevision(revision);
        RateLimitPolicySnapshotCodec.Snapshot snapshot = adminCodec.decode(history.getSnapshotJson());
        LocalDateTime now = LocalDateTime.now();
        String actor = actor();
        for (RateLimitPolicyVo row : snapshot.policies()) {
            if (row.getId() == null) {
                row.setId(IdWorker.getId());
            }
            if (row.getVersion() == null || row.getVersion() < 1L) {
                row.setVersion(1L);
            }
            if (row.getCreateBy() == null) {
                row.setCreateBy(actor);
            }
            if (row.getCreateTime() == null) {
                row.setCreateTime(now);
            }
            row.setUpdateBy(actor);
            row.setUpdateTime(now);
        }
        repository.replaceAll(snapshot.policies());
        return publishSnapshot(actor);
    }

    public RateLimitRuntimeVo runtime() {
        RateLimitRuntimeVo vo = new RateLimitRuntimeVo();
        vo.setRevision(registry.revision());
        Instant refreshedAt = registry.lastSuccessfulRefreshAt();
        vo.setSnapshotLoadedAt(Instant.EPOCH.equals(refreshedAt) ? null : refreshedAt);
        vo.setLastSuccessfulRefreshAt(vo.getSnapshotLoadedAt());
        if (vo.getLastSuccessfulRefreshAt() != null) {
            vo.setSnapshotAgeMs(Duration.between(refreshedAt, Instant.now()).toMillis());
        }
        vo.setSnapshotStale(registry.isSnapshotStale(rateLimitProperties.getStaleSnapshotMaxAge()));
        vo.setBackend(rateLimitProperties.getBackend());
        vo.setSourceUnavailable(registry.sourceUnavailable());
        vo.setPublishFailed(registry.publishFailed());
        List<RateLimitEffectivePolicyVo> effective = listEffectivePolicies();
        vo.setEffectivePolicies(effective);
        vo.setAvailablePolicies(effective.stream().map(RateLimitEffectivePolicyVo::getPolicyCode).toList());
        return vo;
    }

    public RateLimitBucketsVo buckets(Integer limit) {
        int max = limit == null || limit < 1 ? DEFAULT_BUCKET_LIMIT : Math.min(limit, 2000);
        RateLimitBucketsVo vo = new RateLimitBucketsVo();
        vo.setBackend(rateLimitProperties.getBackend());
        if (bucketInspector == null) {
            vo.setObservedAt(Instant.now());
            vo.setTruncated(false);
            vo.setBuckets(List.of());
            return vo;
        }
        RateLimitBucketSnapshot snapshot = bucketInspector.inspect(max);
        vo.setObservedAt(snapshot.observedAt());
        vo.setBackend(snapshot.backend() == null ? rateLimitProperties.getBackend() : snapshot.backend());
        vo.setTruncated(snapshot.truncated());
        vo.setBuckets(snapshot.buckets().stream().map(this::toBucketVo).toList());
        return vo;
    }

    private RateLimitBucketVo toBucketVo(RateLimitBucketView view) {
        RateLimitBucketVo item = new RateLimitBucketVo();
        item.setPolicyCode(view.policyCode());
        item.setSubject(view.subject());
        item.setAlgorithm(view.algorithm());
        item.setLimitCount(view.limit());
        item.setRemaining(view.remaining());
        item.setRetryAfterMs(view.retryAfterMs());
        item.setResetAtMs(view.resetAtMs());
        return item;
    }

    private List<RateLimitEffectivePolicyVo> listEffectivePolicies() {
        Set<String> codes = new LinkedHashSet<>();
        codes.addAll(registry.yamlBaseline().keySet());
        codes.addAll(registry.dynamicSnapshot().keySet());
        codes.addAll(registry.builtins().keySet());
        List<RateLimitEffectivePolicyVo> rows = new ArrayList<>();
        for (String code : codes) {
            RateLimitPolicy policy = registry.find(code).orElse(null);
            if (policy == null) {
                continue;
            }
            RateLimitEffectivePolicyVo item = new RateLimitEffectivePolicyVo();
            item.setPolicyCode(policy.policyCode());
            item.setAlgorithm(policy.algorithm().name());
            item.setLimitCount(policy.limit());
            item.setPeriodMs(policy.period().toMillis());
            item.setBurst(policy.burst());
            item.setStoreFailurePolicy(policy.storeFailurePolicy().name());
            item.setEnabled(policy.enabled());
            item.setSource(effectiveSource(code));
            rows.add(item);
        }
        return rows;
    }

    private String effectiveSource(String policyCode) {
        if (registry.dynamicSnapshot().containsKey(policyCode)) {
            return "dynamic";
        }
        if (registry.yamlBaseline().containsKey(policyCode)) {
            return "yaml";
        }
        return "builtin";
    }

    public void retryPublishLatest() {
        List<RateLimitPolicyVo> all = repository.findAll();
        long revision = repository.latestRevision().orElse(0L);
        if (revision <= 0L) {
            return;
        }
        String runtimeJson = runtimeCodec.encode(revision, adminCodec.toRuntimeMap(all).values());
        tryPublish(revision, runtimeJson);
    }

    private RateLimitPolicyVo create(RateLimitPolicySave body) {
        String code = requireText(body.getPolicyCode(), "策略编码不能为空");
        if (repository.findByCode(code).isPresent()) {
            throw new PlatformException("策略编码已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        String actor = actor();
        RateLimitPolicyVo row = new RateLimitPolicyVo();
        row.setId(IdWorker.getId());
        row.setPolicyCode(code);
        applyMutableFields(row, body, true);
        row.setEnabled(body.getEnabled() == null || Boolean.TRUE.equals(body.getEnabled()));
        row.setVersion(1L);
        row.setCreateBy(actor);
        row.setCreateTime(now);
        row.setUpdateBy(actor);
        row.setUpdateTime(now);
        repository.insert(row);
        publishSnapshot(actor);
        return get(row.getId());
    }

    private RateLimitPolicyVo update(RateLimitPolicySave body) {
        RateLimitPolicyVo existing = get(body.getId());
        if (body.getVersion() == null) {
            throw new PlatformException("缺少版本号，无法保存");
        }
        long expected = body.getVersion();
        if (body.getPolicyCode() != null) {
            String code = requireText(body.getPolicyCode(), "策略编码不能为空");
            repository.findByCode(code).ifPresent(other -> {
                if (!other.getId().equals(existing.getId())) {
                    throw new PlatformException("策略编码已存在");
                }
            });
            existing.setPolicyCode(code);
        }
        applyMutableFields(existing, body, false);
        if (body.getEnabled() != null) {
            existing.setEnabled(body.getEnabled());
        }
        existing.setVersion(expected + 1L);
        existing.setUpdateBy(actor());
        existing.setUpdateTime(LocalDateTime.now());
        if (repository.updateOptimistic(existing, expected) == 0) {
            throw new RateLimitVersionConflictException(repository.findById(existing.getId()).orElse(null));
        }
        publishSnapshot(actor());
        return get(existing.getId());
    }

    private RateLimitPolicyVo setEnabled(Long id, boolean enabled) {
        RateLimitPolicyVo existing = get(id);
        long expected = existing.getVersion() == null ? 0L : existing.getVersion();
        String actor = actor();
        LocalDateTime now = LocalDateTime.now();
        if (repository.updateEnabled(id, enabled, expected, expected + 1L, actor, now) == 0) {
            throw new RateLimitVersionConflictException(repository.findById(id).orElse(null));
        }
        publishSnapshot(actor);
        return get(id);
    }

    private long publishSnapshot(String actor) {
        List<RateLimitPolicyVo> all = repository.findAll();
        long revision = repository.insertRevision("{}", actor, LocalDateTime.now());
        String adminJson = adminCodec.encode(revision, all);
        repository.updateRevisionSnapshot(revision, adminJson);
        String runtimeJson = runtimeCodec.encode(revision, adminCodec.toRuntimeMap(all).values());
        Map<String, cn.miniants.platform.ratelimit.RateLimitPolicy> runtime = adminCodec.toRuntimeMap(all);

        Runnable afterCommit = () -> {
            registry.applyDynamicRevision(runtime, revision, Instant.now());
            tryPublish(revision, runtimeJson);
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    afterCommit.run();
                }
            });
        } else {
            afterCommit.run();
        }
        return revision;
    }

    private void tryPublish(long revision, String runtimeJson) {
        try {
            publisher.publish(revision, runtimeJson);
            registry.markPublishFailed(false);
        } catch (RuntimeException ex) {
            registry.markPublishFailed(true);
            metrics.recordPublishError(rateLimitProperties.getBackend());
            log.error("限流策略快照已写入本机但远程发布失败 revision={}", revision, ex);
        }
    }

    private void applyMutableFields(RateLimitPolicyVo target, RateLimitPolicySave body, boolean creating) {
        if (creating || body.getAlgorithm() != null) {
            target.setAlgorithm(parseAlgorithm(body.getAlgorithm()).name());
        }
        if (creating || body.getLimitCount() != null) {
            target.setLimitCount(requirePositive(body.getLimitCount(), "限流阈值必须为正数"));
        }
        if (creating || body.getPeriodMs() != null) {
            target.setPeriodMs(requirePositive(body.getPeriodMs(), "限流周期必须为正数"));
        }
        if (creating || body.getBurst() != null) {
            Long burst = body.getBurst();
            target.setBurst(burst == null
                    ? target.getLimitCount()
                    : requirePositive(burst, "突发容量必须为正数"));
        } else if (target.getBurst() == null) {
            target.setBurst(target.getLimitCount());
        }
        if (creating || body.getStoreFailurePolicy() != null) {
            target.setStoreFailurePolicy(parseFailure(body.getStoreFailurePolicy()).name());
        }
        if (body.getRemark() != null) {
            target.setRemark(blankToNull(body.getRemark()));
        }
    }

    private static RateLimitAlgorithm parseAlgorithm(String raw) {
        if (raw == null || raw.isBlank()) {
            return RateLimitAlgorithm.GCRA;
        }
        try {
            return RateLimitAlgorithm.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new PlatformException("不支持的限流算法: " + raw);
        }
    }

    private static StoreFailurePolicy parseFailure(String raw) {
        if (raw == null || raw.isBlank()) {
            return StoreFailurePolicy.DENY;
        }
        try {
            return StoreFailurePolicy.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new PlatformException("不支持的存储失败策略: " + raw);
        }
    }

    private static long requirePositive(Long value, String message) {
        if (value == null || value <= 0L) {
            throw new PlatformException(message);
        }
        return value;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new PlatformException(message);
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String actor() {
        CurrentUser user = CurrentUser.find();
        if (user == null || user.username() == null || user.username().isBlank()) {
            return "system";
        }
        return user.username();
    }

    private static Map<String, RateLimitPolicyVo> indexByCode(List<RateLimitPolicyVo> policies) {
        Map<String, RateLimitPolicyVo> map = new LinkedHashMap<>();
        for (RateLimitPolicyVo policy : policies) {
            map.put(policy.getPolicyCode(), policy);
        }
        return map;
    }

    private static boolean sameRuntime(RateLimitPolicyVo left, RateLimitPolicyVo right) {
        return Objects.equals(left.getAlgorithm(), right.getAlgorithm())
                && Objects.equals(left.getLimitCount(), right.getLimitCount())
                && Objects.equals(left.getPeriodMs(), right.getPeriodMs())
                && Objects.equals(left.getBurst(), right.getBurst())
                && Objects.equals(left.getStoreFailurePolicy(), right.getStoreFailurePolicy())
                && Objects.equals(left.getEnabled(), right.getEnabled());
    }
}
