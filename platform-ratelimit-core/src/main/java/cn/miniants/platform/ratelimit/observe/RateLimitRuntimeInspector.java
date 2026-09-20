package cn.miniants.platform.ratelimit.observe;

import cn.miniants.platform.ratelimit.config.RateLimitProperties;
import cn.miniants.platform.ratelimit.policy.CompositeRateLimitPolicyRegistry;
import cn.miniants.platform.ratelimit.spi.RateLimitPolicyRegistry;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 汇总运行时策略状态，供 Actuator 与管理端 runtime 接口复用。
 */
public class RateLimitRuntimeInspector {

    private final RateLimitPolicyRegistry registry;
    private final RateLimitProperties properties;

    public RateLimitRuntimeInspector(RateLimitPolicyRegistry registry, RateLimitProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    public RateLimitRuntimeSnapshot snapshot() {
        if (registry instanceof CompositeRateLimitPolicyRegistry composite) {
            Instant refreshedAt = composite.lastSuccessfulRefreshAt();
            boolean never = Instant.EPOCH.equals(refreshedAt);
            Long age = never ? null : Duration.between(refreshedAt, Instant.now()).toMillis();
            Set<String> codes = new LinkedHashSet<>();
            codes.addAll(composite.dynamicSnapshot().keySet());
            codes.addAll(composite.yamlBaseline().keySet());
            codes.addAll(composite.builtins().keySet());
            return new RateLimitRuntimeSnapshot(
                    properties.getBackend(),
                    composite.revision(),
                    never ? null : refreshedAt,
                    age,
                    composite.isSnapshotStale(properties.getStaleSnapshotMaxAge()),
                    composite.sourceUnavailable(),
                    composite.publishFailed(),
                    List.copyOf(codes));
        }
        List<String> codes = new ArrayList<>();
        if (properties.getPolicies() != null) {
            codes.addAll(properties.getPolicies().keySet());
        }
        return new RateLimitRuntimeSnapshot(
                properties.getBackend(), 0L, null, null, false, false, false, List.copyOf(codes));
    }
}
