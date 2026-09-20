package cn.miniants.platform.ratelimit.webflux;

import cn.miniants.platform.ratelimit.annotation.RateLimit;
import cn.miniants.platform.ratelimit.annotation.RateLimitAnnotationBinding;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.reactive.function.server.HandlerFilterFunction;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 功能式路由限流包装：在 handler 前申请额度。
 *
 * <pre>{@code
 * RouterFunctions.route()
 *   .GET("/ping", this::ping)
 *   .filter(RateLimitHandlerFilterFunction.of(rateLimiter, support, bindings))
 * }</pre>
 */
public final class RateLimitHandlerFilterFunction
        implements HandlerFilterFunction<ServerResponse, ServerResponse> {

    private final RateLimitWebFilter webFilter;
    private final List<RateLimitAnnotationBinding> bindings;

    private RateLimitHandlerFilterFunction(RateLimitWebFilter webFilter, List<RateLimitAnnotationBinding> bindings) {
        this.webFilter = Objects.requireNonNull(webFilter, "webFilter");
        this.bindings = List.copyOf(Objects.requireNonNull(bindings, "bindings"));
    }

    public static RateLimitHandlerFilterFunction of(
            RateLimitWebFilter webFilter,
            List<RateLimitAnnotationBinding> bindings) {
        return new RateLimitHandlerFilterFunction(webFilter, bindings);
    }

    /**
     * 从声明式注解构造绑定（供测试或手工装配）。
     */
    public static List<RateLimitAnnotationBinding> bindingsFrom(RateLimit... annotations) {
        List<RateLimitAnnotationBinding> list = new ArrayList<>();
        int i = 0;
        for (RateLimit annotation : annotations) {
            list.add(RateLimitAnnotationBinding.parse(
                    annotation, "functional#" + i, "@RateLimit:functional[" + i + "]"));
            i++;
        }
        return list;
    }

    public static List<RateLimitAnnotationBinding> bindingsFromElement(Object annotated) {
        return new ArrayList<>(AnnotatedElementUtils.findMergedRepeatableAnnotations(
                annotated.getClass(), RateLimit.class)
                .stream()
                .map(a -> RateLimitAnnotationBinding.parse(a, annotated.getClass().getSimpleName(),
                        "@RateLimit:" + annotated.getClass().getName()))
                .toList());
    }

    @Override
    public Mono<ServerResponse> filter(ServerRequest request, HandlerFunction<ServerResponse> next) {
        return webFilter.applyBindings(request.exchange(), bindings).then(next.handle(request));
    }
}
