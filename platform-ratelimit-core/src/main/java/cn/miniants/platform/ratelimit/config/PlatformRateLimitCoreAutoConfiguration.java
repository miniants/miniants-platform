package cn.miniants.platform.ratelimit.config;

import cn.miniants.platform.ratelimit.RateLimitBackend;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.client.ClientAddressResolver;
import cn.miniants.platform.ratelimit.local.CompositeLocalRateLimiter;
import cn.miniants.platform.ratelimit.local.DelegatingReactiveRateLimiter;
import cn.miniants.platform.ratelimit.local.LocalRateLimitBucketInspector;
import cn.miniants.platform.ratelimit.local.StaleAwareRateLimiter;
import cn.miniants.platform.ratelimit.metrics.RateLimitMetricsRecorder;
import cn.miniants.platform.ratelimit.observe.RateLimitRuntimeInspector;
import cn.miniants.platform.ratelimit.policy.CompositeRateLimitPolicyRegistry;
import cn.miniants.platform.ratelimit.snapshot.RateLimitPolicySnapshotCodec;
import cn.miniants.platform.ratelimit.spi.RateLimitBucketInspector;
import cn.miniants.platform.ratelimit.spi.RateLimitClock;
import cn.miniants.platform.ratelimit.spi.RateLimitPolicyContributor;
import cn.miniants.platform.ratelimit.spi.RateLimitPolicyRegistry;
import cn.miniants.platform.ratelimit.spi.RateLimiter;
import cn.miniants.platform.ratelimit.spi.ReactiveRateLimiter;
import cn.miniants.platform.ratelimit.spi.SystemRateLimitClock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 限流核心自动配置：时钟、组合策略注册表、本地引擎。
 */
@AutoConfiguration
@EnableConfigurationProperties(RateLimitProperties.class)
@ConditionalOnProperty(prefix = "platform.ratelimit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PlatformRateLimitCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RateLimitClock.class)
    public RateLimitClock rateLimitClock() {
        return SystemRateLimitClock.INSTANCE;
    }

    @Bean
    @ConditionalOnMissingBean(RateLimitPolicyRegistry.class)
    public CompositeRateLimitPolicyRegistry rateLimitPolicyRegistry(
            RateLimitProperties properties,
            ObjectProvider<RateLimitPolicyContributor> contributors) {
        Map<String, RateLimitPolicy> builtins = new LinkedHashMap<>();
        for (RateLimitPolicyContributor contributor : contributors.orderedStream().toList()) {
            Collection<RateLimitPolicy> contributed = contributor.contribute();
            if (contributed == null) {
                continue;
            }
            for (RateLimitPolicy policy : contributed) {
                if (policy == null) {
                    continue;
                }
                RateLimitPolicy previous = builtins.put(policy.policyCode(), policy);
                if (previous != null) {
                    throw new IllegalStateException("重复的内置限流策略编码: " + policy.policyCode());
                }
            }
        }
        Map<String, RateLimitPolicy> yaml = new LinkedHashMap<>();
        Map<String, RateLimitProperties.PolicyDefinition> definitions = properties.getPolicies();
        if (definitions != null) {
            for (Map.Entry<String, RateLimitProperties.PolicyDefinition> entry : definitions.entrySet()) {
                RateLimitProperties.PolicyDefinition definition = entry.getValue();
                if (definition == null) {
                    continue;
                }
                RateLimitPolicy policy = definition.toPolicy(entry.getKey());
                if (yaml.put(policy.policyCode(), policy) != null) {
                    throw new IllegalStateException("重复的 YAML 限流策略编码: " + policy.policyCode());
                }
            }
        }
        return new CompositeRateLimitPolicyRegistry(builtins, yaml);
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitPolicySnapshotCodec rateLimitPolicySnapshotCodec(ObjectProvider<ObjectMapper> objectMapper) {
        return new RateLimitPolicySnapshotCodec(objectMapper.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnMissingBean(ClientAddressResolver.class)
    public ClientAddressResolver clientAddressResolver(RateLimitProperties properties) {
        return ClientAddressResolver.of(properties.getTrustedProxies());
    }

    @Bean
    @ConditionalOnMissingBean(RateLimitRuntimeInspector.class)
    public RateLimitRuntimeInspector rateLimitRuntimeInspector(
            RateLimitPolicyRegistry registry, RateLimitProperties properties) {
        return new RateLimitRuntimeInspector(registry, properties);
    }

    @Bean
    @ConditionalOnMissingBean(RateLimiter.class)
    @ConditionalOnProperty(prefix = "platform.ratelimit", name = "backend", havingValue = "local", matchIfMissing = true)
    public RateLimiter localRateLimiter(
            RateLimitClock clock,
            RateLimitProperties properties,
            RateLimitPolicyRegistry registry,
            ObjectProvider<RateLimitMetricsRecorder> metrics) {
        if (properties.backendType() != RateLimitBackend.LOCAL) {
            throw new IllegalStateException("当前限流后端不是 local");
        }
        return new StaleAwareRateLimiter(
                new CompositeLocalRateLimiter(clock),
                registry,
                properties,
                metrics.getIfAvailable(() -> RateLimitMetricsRecorder.NOOP),
                RateLimitBackend.LOCAL);
    }

    @Bean
    @ConditionalOnMissingBean(RateLimitBucketInspector.class)
    @ConditionalOnBean(RateLimiter.class)
    @ConditionalOnProperty(prefix = "platform.ratelimit", name = "backend", havingValue = "local", matchIfMissing = true)
    public RateLimitBucketInspector localRateLimitBucketInspector(
            RateLimiter rateLimiter, RateLimitPolicyRegistry registry, RateLimitClock clock) {
        return new LocalRateLimitBucketInspector(rateLimiter, registry, clock);
    }

    @Bean
    @ConditionalOnMissingBean(ReactiveRateLimiter.class)
    @ConditionalOnBean(RateLimiter.class)
    @ConditionalOnProperty(prefix = "platform.ratelimit", name = "backend", havingValue = "local", matchIfMissing = true)
    public ReactiveRateLimiter localReactiveRateLimiter(RateLimiter rateLimiter) {
        return new DelegatingReactiveRateLimiter(rateLimiter);
    }
}
