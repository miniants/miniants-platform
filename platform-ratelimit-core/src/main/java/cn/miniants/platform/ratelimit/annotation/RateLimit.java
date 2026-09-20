package cn.miniants.platform.ratelimit.annotation;

import cn.miniants.platform.ratelimit.RateLimitAlgorithm;
import cn.miniants.platform.ratelimit.RateLimitSubjectType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式限流。命名策略与内联参数互斥：要么填 {@link #policy()}，要么同时填 {@link #limit()} 与 {@link #period()}。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(RateLimits.class)
public @interface RateLimit {

    /**
     * 命名策略编码（对应注册表 / YAML）。非空时不得再填 limit/period。
     */
    String policy() default "";

    /**
     * 内联阈值；须与 {@link #period()} 同时为正/非空。
     */
    long limit() default -1;

    /**
     * 内联周期，如 {@code 1m}、{@code 30s}。
     */
    String period() default "";

    /**
     * 限流主体类型。
     */
    RateLimitSubjectType subject() default RateLimitSubjectType.CLIENT_IP;

    /**
     * {@link RateLimitSubjectType#CUSTOM} 时的命名 {@code RateLimitSubjectResolver} Bean。
     */
    String resolver() default "";

    /**
     * 本次消耗额度。
     */
    int cost() default 1;

    /**
     * 仅内联策略生效；命名策略以注册表为准。
     */
    RateLimitAlgorithm algorithm() default RateLimitAlgorithm.GCRA;
}
