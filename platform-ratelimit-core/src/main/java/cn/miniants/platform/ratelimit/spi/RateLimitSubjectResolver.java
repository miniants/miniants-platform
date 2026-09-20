package cn.miniants.platform.ratelimit.spi;

/**
 * 命名主体解析器（Web 层按 Bean 名注入）。核心模块只定义契约。
 *
 * <p>适配层传入各自的请求上下文（MVC {@code HttpServletRequest} / WebFlux {@code ServerWebExchange} 等）。
 */
@FunctionalInterface
public interface RateLimitSubjectResolver {

    /**
     * @param requestContext 适配层请求上下文，可为 null（解析器自行处理）
     * @return 非空主体键
     */
    String resolve(Object requestContext);
}
