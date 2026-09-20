package cn.miniants.platform.ratelimit.webflux;

import cn.miniants.platform.ratelimit.RateLimitExceededException;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.RateLimitRequest;
import cn.miniants.platform.ratelimit.annotation.RateLimitAnnotationBinding;
import cn.miniants.platform.ratelimit.spi.ReactiveRateLimiter;
import cn.miniants.platform.ratelimit.support.RateLimitHttpHeaders;
import cn.miniants.platform.ratelimit.support.RateLimitRejectLogger;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.List;
import java.util.Objects;

/**
 * WebFlux 限流过滤器：按启动期索引的路由绑定申请额度。
 */
public class RateLimitWebFilter implements WebFilter, Ordered {

    private final ReactiveRateLimiter rateLimiter;
    private final RateLimitWebFluxSupport support;
    private final RateLimitRouteIndex routeIndex;
    private final int order;

    public RateLimitWebFilter(
            ReactiveRateLimiter rateLimiter,
            RateLimitWebFluxSupport support,
            RateLimitRouteIndex routeIndex) {
        this(rateLimiter, support, routeIndex, -50);
    }

    public RateLimitWebFilter(
            ReactiveRateLimiter rateLimiter,
            RateLimitWebFluxSupport support,
            RateLimitRouteIndex routeIndex,
            int order) {
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.support = Objects.requireNonNull(support, "support");
        this.routeIndex = Objects.requireNonNull(routeIndex, "routeIndex");
        this.order = order;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        List<RateLimitAnnotationBinding> bindings = routeIndex.match(exchange);
        Mono<Void> gated = bindings.isEmpty() ? Mono.empty() : applyBindings(exchange, bindings);
        return gated
                .then(chain.filter(exchange))
                .contextWrite(Context.of(RateLimitWebFluxSupport.EXCHANGE_CONTEXT_KEY, exchange));
    }

    Mono<Void> applyBindings(ServerWebExchange exchange, List<RateLimitAnnotationBinding> bindings) {
        Mono<Void> steps = Mono.empty();
        for (RateLimitAnnotationBinding binding : bindings) {
            RateLimitPolicy policy = support.resolvePolicy(binding);
            if (!policy.enabled()) {
                continue;
            }
            String subject = support.resolveSubject(binding, exchange);
            RateLimitRequest request = RateLimitRequest.of(policy.policyCode(), subject, binding.cost());
            steps = steps.then(rateLimiter.acquire(request, policy).flatMap(decision -> {
                if (!decision.allowed()) {
                    exchange.getAttributes().put(RateLimitHttpHeaders.ACCESS_ERR_ATTR, "RateLimitExceeded");
                    exchange.getAttributes().put(
                            RateLimitHttpHeaders.ACCESS_REASON_ATTR,
                            "rate-limit:" + decision.policyCode() + ":" + decision.outcome());
                    RateLimitRejectLogger.denied(decision, subject);
                    return Mono.error(new RateLimitExceededException(decision));
                }
                return Mono.empty();
            }));
        }
        return steps;
    }

    @Override
    public int getOrder() {
        return order;
    }
}
