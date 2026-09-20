package cn.miniants.platform.ratelimit.annotation;

import cn.miniants.platform.ratelimit.RateLimitAlgorithm;
import cn.miniants.platform.ratelimit.RateLimitDurations;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.RateLimitSubjectType;
import cn.miniants.platform.ratelimit.StoreFailurePolicy;

import java.util.Objects;

/**
 * 解析后的 {@link RateLimit} 绑定（启动校验与运行时共用）。
 */
public final class RateLimitAnnotationBinding {

    private final String location;
    private final boolean namedPolicy;
    private final String policyCode;
    private final RateLimitPolicy inlinePolicy;
    private final RateLimitSubjectType subjectType;
    private final String resolverBeanName;
    private final int cost;

    private RateLimitAnnotationBinding(
            String location,
            boolean namedPolicy,
            String policyCode,
            RateLimitPolicy inlinePolicy,
            RateLimitSubjectType subjectType,
            String resolverBeanName,
            int cost) {
        this.location = Objects.requireNonNull(location, "location");
        this.namedPolicy = namedPolicy;
        this.policyCode = Objects.requireNonNull(policyCode, "policyCode");
        this.inlinePolicy = inlinePolicy;
        this.subjectType = Objects.requireNonNull(subjectType, "subjectType");
        this.resolverBeanName = resolverBeanName == null ? "" : resolverBeanName;
        this.cost = cost;
    }

    /**
     * 解析并校验互斥规则；失败抛出带中文说明的 {@link IllegalStateException}。
     *
     * @param annotation    注解
     * @param location      如 {@code DemoController#ping}
     * @param syntheticCode 内联策略用的合成编码
     */
    public static RateLimitAnnotationBinding parse(RateLimit annotation, String location, String syntheticCode) {
        Objects.requireNonNull(annotation, "annotation");
        String loc = location == null || location.isBlank() ? "<unknown>" : location;
        String policy = annotation.policy() == null ? "" : annotation.policy().trim();
        boolean hasPolicy = !policy.isBlank();
        boolean hasInline = annotation.limit() > 0 && annotation.period() != null && !annotation.period().isBlank();
        boolean partialInline = (annotation.limit() > 0) ^ (annotation.period() != null && !annotation.period().isBlank());

        if (hasPolicy && hasInline) {
            throw new IllegalStateException(
                    "限流注解不能同时指定命名策略与内联参数: " + loc);
        }
        if (hasPolicy && (annotation.limit() > 0 || (annotation.period() != null && !annotation.period().isBlank()))) {
            throw new IllegalStateException(
                    "限流注解不能同时指定命名策略与内联参数: " + loc);
        }
        if (!hasPolicy && !hasInline) {
            if (partialInline) {
                throw new IllegalStateException(
                        "限流内联参数须同时指定 limit>0 与非空 period: " + loc);
            }
            throw new IllegalStateException(
                    "限流注解须指定 policy，或同时指定 limit 与 period: " + loc);
        }
        if (annotation.cost() <= 0) {
            throw new IllegalStateException("限流 cost 必须为正数: " + loc);
        }

        RateLimitSubjectType subjectType = annotation.subject() == null
                ? RateLimitSubjectType.CLIENT_IP
                : annotation.subject();
        String resolver = annotation.resolver() == null ? "" : annotation.resolver().trim();
        if (subjectType == RateLimitSubjectType.CUSTOM && resolver.isBlank()) {
            throw new IllegalStateException(
                    "限流主体为 CUSTOM 时必须指定 resolver Bean 名: " + loc);
        }
        if (subjectType != RateLimitSubjectType.CUSTOM && !resolver.isBlank()) {
            throw new IllegalStateException(
                    "仅 CUSTOM 主体可指定 resolver: " + loc);
        }

        if (hasPolicy) {
            return new RateLimitAnnotationBinding(
                    loc, true, policy, null, subjectType, resolver, annotation.cost());
        }

        RateLimitAlgorithm algorithm = annotation.algorithm() == null
                ? RateLimitAlgorithm.GCRA
                : annotation.algorithm();
        String code = syntheticCode == null || syntheticCode.isBlank() ? loc : syntheticCode;
        RateLimitPolicy inline = RateLimitPolicy.builder()
                .policyCode(code)
                .algorithm(algorithm)
                .limit(annotation.limit())
                .period(RateLimitDurations.parse(annotation.period()))
                .storeFailurePolicy(StoreFailurePolicy.DENY)
                .enabled(true)
                .version(0)
                .build();
        return new RateLimitAnnotationBinding(
                loc, false, code, inline, subjectType, resolver, annotation.cost());
    }

    public String location() {
        return location;
    }

    public boolean namedPolicy() {
        return namedPolicy;
    }

    public String policyCode() {
        return policyCode;
    }

    public RateLimitPolicy inlinePolicy() {
        return inlinePolicy;
    }

    public RateLimitSubjectType subjectType() {
        return subjectType;
    }

    public String resolverBeanName() {
        return resolverBeanName;
    }

    public int cost() {
        return cost;
    }
}
