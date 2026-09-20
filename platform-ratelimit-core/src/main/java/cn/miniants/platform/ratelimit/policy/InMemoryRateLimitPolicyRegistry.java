package cn.miniants.platform.ratelimit.policy;

import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.spi.RateLimitPolicyRegistry;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 ConcurrentHashMap 的本地策略注册表。
 */
public class InMemoryRateLimitPolicyRegistry implements RateLimitPolicyRegistry {

    private final ConcurrentHashMap<String, RateLimitPolicy> policies = new ConcurrentHashMap<>();

    public InMemoryRateLimitPolicyRegistry() {
    }

    public InMemoryRateLimitPolicyRegistry(Collection<RateLimitPolicy> initial) {
        if (initial != null) {
            for (RateLimitPolicy policy : initial) {
                putInternal(policy);
            }
        }
    }

    public InMemoryRateLimitPolicyRegistry(Map<String, RateLimitPolicy> initial) {
        if (initial != null) {
            for (RateLimitPolicy policy : initial.values()) {
                putInternal(policy);
            }
        }
    }

    public void put(RateLimitPolicy policy) {
        putInternal(policy);
    }

    public void putAll(Collection<RateLimitPolicy> values) {
        if (values == null) {
            return;
        }
        for (RateLimitPolicy policy : values) {
            putInternal(policy);
        }
    }

    public void remove(String policyCode) {
        if (policyCode != null) {
            policies.remove(policyCode);
        }
    }

    public void replaceAll(Collection<RateLimitPolicy> values) {
        policies.clear();
        putAll(values);
    }

    public Map<String, RateLimitPolicy> snapshot() {
        return Map.copyOf(policies);
    }

    @Override
    public Optional<RateLimitPolicy> find(String policyCode) {
        if (policyCode == null || policyCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(policies.get(policyCode));
    }

    private void putInternal(RateLimitPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        policies.put(policy.policyCode(), policy);
    }
}
