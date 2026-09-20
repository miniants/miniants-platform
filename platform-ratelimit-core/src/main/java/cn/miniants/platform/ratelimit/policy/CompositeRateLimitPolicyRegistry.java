package cn.miniants.platform.ratelimit.policy;

import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.spi.RateLimitPolicyRegistry;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 组合策略注册表：动态快照（DB/Redis）&gt; YAML 基线 &gt; 内置。
 */
public class CompositeRateLimitPolicyRegistry implements RateLimitPolicyRegistry {

    private final Map<String, RateLimitPolicy> builtins;
    private final Map<String, RateLimitPolicy> yamlBaseline;
    private final AtomicReference<DynamicLayer> dynamic = new AtomicReference<>(DynamicLayer.EMPTY);
    private final AtomicBoolean sourceUnavailable = new AtomicBoolean(false);
    private final AtomicBoolean publishFailed = new AtomicBoolean(false);

    public CompositeRateLimitPolicyRegistry(
            Map<String, RateLimitPolicy> builtins,
            Map<String, RateLimitPolicy> yamlBaseline) {
        this.builtins = copy(builtins);
        this.yamlBaseline = copy(yamlBaseline);
    }

    public CompositeRateLimitPolicyRegistry(Collection<RateLimitPolicy> yamlPolicies) {
        this(Map.of(), toMap(yamlPolicies));
    }

    public CompositeRateLimitPolicyRegistry(Map<String, RateLimitPolicy> yamlBaseline) {
        this(Map.of(), yamlBaseline);
    }

    /**
     * 原子 revision CAS：低 revision 拒绝；相同 revision 只刷新健康时间；更高 revision 替换策略图。
     */
    public DynamicPolicyApplyResult applyDynamicRevision(
            Map<String, RateLimitPolicy> policies,
            long revision,
            Instant contactedAt) {
        Instant now = contactedAt == null ? Instant.now() : contactedAt;
        Map<String, RateLimitPolicy> copy = copy(policies);
        while (true) {
            DynamicLayer current = dynamic.get();
            if (revision < current.revision()) {
                return DynamicPolicyApplyResult.REJECTED_STALE;
            }
            if (revision == current.revision()) {
                DynamicLayer touched = current.touch(now);
                if (dynamic.compareAndSet(current, touched)) {
                    return DynamicPolicyApplyResult.TOUCHED;
                }
                continue;
            }
            DynamicLayer next = new DynamicLayer(copy, revision, now, now);
            if (dynamic.compareAndSet(current, next)) {
                return DynamicPolicyApplyResult.APPLIED;
            }
        }
    }

    /**
     * 管理端本机 LKG 或测试直接写入。仍走 CAS。
     */
    public void publishDynamicPolicies(Map<String, RateLimitPolicy> policies, long revision) {
        applyDynamicRevision(policies, revision, Instant.now());
    }

    /**
     * @deprecated 使用 {@link #applyDynamicRevision(Map, long, Instant)}
     */
    @Deprecated
    public void replaceDynamicSnapshot(Map<String, RateLimitPolicy> snapshot) {
        long revision = Math.max(1L, dynamic.get().revision() + 1L);
        applyDynamicRevision(snapshot == null ? Map.of() : snapshot, revision, Instant.now());
    }

    public long revision() {
        return dynamic.get().revision();
    }

    public Instant snapshotLoadedAt() {
        return dynamic.get().loadedAt();
    }

    public Instant lastSuccessfulRefreshAt() {
        return dynamic.get().lastSuccessfulRefreshAt();
    }

    public Map<String, RateLimitPolicy> dynamicSnapshot() {
        return dynamic.get().policies();
    }

    public Map<String, RateLimitPolicy> yamlBaseline() {
        return yamlBaseline;
    }

    public Map<String, RateLimitPolicy> builtins() {
        return builtins;
    }

    public boolean sourceUnavailable() {
        return sourceUnavailable.get();
    }

    public void markSourceUnavailable(boolean unavailable) {
        sourceUnavailable.set(unavailable);
    }

    public boolean publishFailed() {
        return publishFailed.get();
    }

    public void markPublishFailed(boolean failed) {
        publishFailed.set(failed);
    }

    /**
     * 动态层中的命名策略在快照过期后应按策略的 ALLOW/DENY 故障语义执行。
     */
    public boolean shouldApplyStaleFailure(String policyCode, Duration maxAge) {
        if (policyCode == null || policyCode.isBlank() || !isSnapshotStale(maxAge)) {
            return false;
        }
        return dynamic.get().policies().containsKey(policyCode);
    }

    /**
     * 动态快照是否超过最大陈旧时长（从未成功联系策略源时返回 false）。
     */
    public boolean isSnapshotStale(Duration maxAge) {
        Objects.requireNonNull(maxAge, "maxAge");
        DynamicLayer layer = dynamic.get();
        Instant freshness = layer.lastSuccessfulRefreshAt();
        if (layer.revision() <= 0L || Instant.EPOCH.equals(freshness)) {
            return false;
        }
        if (maxAge.isZero() || maxAge.isNegative()) {
            return true;
        }
        return freshness.plus(maxAge).isBefore(Instant.now());
    }

    @Override
    public Optional<RateLimitPolicy> find(String policyCode) {
        if (policyCode == null || policyCode.isBlank()) {
            return Optional.empty();
        }
        RateLimitPolicy fromDynamic = dynamic.get().policies().get(policyCode);
        if (fromDynamic != null) {
            return Optional.of(fromDynamic);
        }
        RateLimitPolicy fromYaml = yamlBaseline.get(policyCode);
        if (fromYaml != null) {
            return Optional.of(fromYaml);
        }
        return Optional.ofNullable(builtins.get(policyCode));
    }

    private static Map<String, RateLimitPolicy> toMap(Collection<RateLimitPolicy> policies) {
        if (policies == null || policies.isEmpty()) {
            return Map.of();
        }
        Map<String, RateLimitPolicy> map = new LinkedHashMap<>();
        for (RateLimitPolicy policy : policies) {
            Objects.requireNonNull(policy, "policy");
            if (map.put(policy.policyCode(), policy) != null) {
                throw new IllegalStateException("重复的限流策略编码: " + policy.policyCode());
            }
        }
        return Map.copyOf(map);
    }

    private static Map<String, RateLimitPolicy> copy(Map<String, RateLimitPolicy> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, RateLimitPolicy> map = new LinkedHashMap<>();
        for (Map.Entry<String, RateLimitPolicy> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                continue;
            }
            map.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(map);
    }

    private record DynamicLayer(
            Map<String, RateLimitPolicy> policies,
            long revision,
            Instant loadedAt,
            Instant lastSuccessfulRefreshAt) {
        static final DynamicLayer EMPTY = new DynamicLayer(Map.of(), 0L, Instant.EPOCH, Instant.EPOCH);

        private DynamicLayer {
            policies = policies == null ? Map.of() : policies;
            loadedAt = loadedAt == null ? Instant.EPOCH : loadedAt;
            lastSuccessfulRefreshAt = lastSuccessfulRefreshAt == null ? Instant.EPOCH : lastSuccessfulRefreshAt;
        }

        private DynamicLayer touch(Instant contactedAt) {
            return new DynamicLayer(policies, revision, loadedAt, contactedAt);
        }
    }
}
