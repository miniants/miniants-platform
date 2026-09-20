package cn.miniants.platform.ratelimit.webflux;

import cn.miniants.platform.ratelimit.annotation.RateLimitAnnotationBinding;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 启动期从注解控制器收集的路径 → 限流绑定索引，供 {@link RateLimitWebFilter} 在 Handler 匹配前使用。
 */
public class RateLimitRouteIndex {

    private final List<Entry> entries = new ArrayList<>();
    private final PathPatternParser parser = new PathPatternParser();

    public synchronized void clear() {
        entries.clear();
    }

    public synchronized void register(
            Set<String> patterns,
            Set<HttpMethod> methods,
            List<RateLimitAnnotationBinding> bindings) {
        if (bindings == null || bindings.isEmpty() || patterns == null || patterns.isEmpty()) {
            return;
        }
        List<PathPattern> pathPatterns = new ArrayList<>();
        for (String pattern : patterns) {
            pathPatterns.add(parser.parse(pattern));
        }
        entries.add(new Entry(List.copyOf(pathPatterns), methods == null ? Set.of() : Set.copyOf(methods),
                List.copyOf(bindings)));
    }

    /**
     * 功能式路由手动登记。
     */
    public void registerPredicate(
            RequestPredicate predicate,
            List<RateLimitAnnotationBinding> bindings) {
        Objects.requireNonNull(predicate, "predicate");
        if (bindings == null || bindings.isEmpty()) {
            return;
        }
        synchronized (this) {
            entries.add(new Entry(List.of(), Set.of(), List.copyOf(bindings), predicate));
        }
    }

    public synchronized List<RateLimitAnnotationBinding> match(ServerWebExchange exchange) {
        PathContainer path = exchange.getRequest().getPath().pathWithinApplication();
        HttpMethod method = exchange.getRequest().getMethod();
        List<RateLimitAnnotationBinding> matched = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.predicate != null) {
                if (entry.predicate.test(ServerRequest.create(exchange, List.of()))) {
                    matched.addAll(entry.bindings);
                }
                continue;
            }
            if (!entry.methods.isEmpty() && method != null && !entry.methods.contains(method)) {
                continue;
            }
            for (PathPattern pattern : entry.patterns) {
                if (pattern.matches(path)) {
                    matched.addAll(entry.bindings);
                    break;
                }
            }
        }
        return matched;
    }

    private record Entry(
            List<PathPattern> patterns,
            Set<HttpMethod> methods,
            List<RateLimitAnnotationBinding> bindings,
            RequestPredicate predicate) {

        Entry(List<PathPattern> patterns, Set<HttpMethod> methods, List<RateLimitAnnotationBinding> bindings) {
            this(patterns, methods, bindings, null);
        }
    }
}
