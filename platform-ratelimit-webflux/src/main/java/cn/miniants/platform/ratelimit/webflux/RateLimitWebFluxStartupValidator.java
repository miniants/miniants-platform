package cn.miniants.platform.ratelimit.webflux;

import cn.miniants.platform.ratelimit.annotation.RateLimitAnnotationBinding;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.result.method.RequestMappingInfo;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 扫描 WebFlux {@code @RequestMapping} 上的 {@code @RateLimit}，校验并写入 {@link RateLimitRouteIndex}。
 */
public class RateLimitWebFluxStartupValidator implements SmartInitializingSingleton {

    private final Collection<RequestMappingHandlerMapping> handlerMappings;
    private final RateLimitWebFluxSupport support;
    private final RateLimitRouteIndex routeIndex;
    private final int filterOrder;

    public RateLimitWebFluxStartupValidator(
            Collection<RequestMappingHandlerMapping> handlerMappings,
            RateLimitWebFluxSupport support,
            RateLimitRouteIndex routeIndex) {
        this(handlerMappings, support, routeIndex, -50);
    }

    public RateLimitWebFluxStartupValidator(
            Collection<RequestMappingHandlerMapping> handlerMappings,
            RateLimitWebFluxSupport support,
            RateLimitRouteIndex routeIndex,
            int filterOrder) {
        this.handlerMappings = List.copyOf(Objects.requireNonNull(handlerMappings, "handlerMappings"));
        this.support = Objects.requireNonNull(support, "support");
        this.routeIndex = Objects.requireNonNull(routeIndex, "routeIndex");
        this.filterOrder = filterOrder;
    }

    public RateLimitWebFluxStartupValidator(
            RequestMappingHandlerMapping handlerMapping,
            RateLimitWebFluxSupport support,
            RateLimitRouteIndex routeIndex) {
        this(List.of(handlerMapping), support, routeIndex);
    }

    @Override
    public void afterSingletonsInstantiated() {
        routeIndex.clear();
        for (RequestMappingHandlerMapping handlerMapping : handlerMappings) {
            Map<RequestMappingInfo, HandlerMethod> map = handlerMapping.getHandlerMethods();
            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : map.entrySet()) {
                register(entry.getKey(), entry.getValue());
            }
        }
    }

    private void register(RequestMappingInfo info, HandlerMethod handlerMethod) {
        List<RateLimitAnnotationBinding> bindings = support.scan(handlerMethod);
        if (bindings.isEmpty()) {
            return;
        }
        support.validateBindings(bindings, filterOrder);
        support.bindingsFor(handlerMethod);

        Set<String> patterns = new LinkedHashSet<>();
        if (info.getPatternsCondition() != null) {
            info.getPatternsCondition().getPatterns()
                    .forEach(p -> patterns.add(p.getPatternString()));
        }
        patterns.addAll(info.getDirectPaths());
        Set<HttpMethod> methods = new LinkedHashSet<>();
        if (info.getMethodsCondition() != null) {
            for (RequestMethod method : info.getMethodsCondition().getMethods()) {
                methods.add(HttpMethod.valueOf(method.name()));
            }
        }
        routeIndex.register(patterns, methods, bindings);
    }
}
