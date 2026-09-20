package cn.miniants.platform.core.advice;

import cn.miniants.platform.core.api.ApiResult;
import cn.miniants.platform.core.api.RawBody;
import org.reactivestreams.Publisher;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.annotation.Order;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.HandlerResult;
import org.springframework.web.reactive.accept.RequestedContentTypeResolver;
import org.springframework.web.reactive.result.method.annotation.ResponseBodyResultHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * WebFlux 成功体包装为 {@link ApiResult}，语义对齐 MVC {@link PlatformResultAdvice}。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PlatformWebFluxResultFilter extends ResponseBodyResultHandler {

    public PlatformWebFluxResultFilter(List<HttpMessageWriter<?>> writers,
            RequestedContentTypeResolver contentTypeResolver) {
        super(writers, contentTypeResolver);
    }

    public PlatformWebFluxResultFilter(List<HttpMessageWriter<?>> writers,
            RequestedContentTypeResolver contentTypeResolver,
            ReactiveAdapterRegistry registry) {
        super(writers, contentTypeResolver, registry);
    }

    @Override
    public boolean supports(HandlerResult result) {
        if (!super.supports(result)) {
            return false;
        }
        Object handler = result.getHandler();
        if (handler instanceof HandlerMethod method) {
            return !hasRawBody(method);
        }
        return true;
    }

    @Override
    public Mono<Void> handleResult(ServerWebExchange exchange, HandlerResult result) {
        Object returnValue = result.getReturnValue();
        Object handler = result.getHandler();
        if (handler instanceof HandlerMethod method && hasRawBody(method)) {
            return super.handleResult(exchange, result);
        }
        if (alreadyWrapped(returnValue)) {
            return super.handleResult(exchange, result);
        }
        ReactiveAdapter adapter = getAdapter(result);
        if (adapter != null) {
            Publisher<?> publisher = adapter.toPublisher(returnValue);
            if (adapter.isMultiValue()) {
                Mono<?> wrapped = Flux.from(publisher).collectList().map(this::wrapValue);
                return super.handleResult(exchange, new HandlerResult(handler, wrapped, result.getReturnTypeSource()));
            }
            Mono<?> wrapped = Mono.from(publisher).map(this::wrapValue);
            return super.handleResult(exchange, new HandlerResult(handler, wrapped, result.getReturnTypeSource()));
        }
        Object wrapped = wrapValue(returnValue);
        return super.handleResult(exchange, new HandlerResult(handler, wrapped, result.getReturnTypeSource()));
    }

    private Object wrapValue(Object body) {
        if (alreadyWrapped(body)) {
            return body;
        }
        return ApiResult.ok(body);
    }

    static boolean alreadyWrapped(Object body) {
        if (body == null) {
            return false;
        }
        if (body instanceof ApiResult<?>) {
            return true;
        }
        Class<?> type = body.getClass();
        while (type != null && type != Object.class) {
            if ("ApiResult".equals(type.getSimpleName())) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static boolean hasRawBody(HandlerMethod method) {
        MethodParameter returnType = method.getReturnType();
        return returnType.hasMethodAnnotation(RawBody.class)
                || method.getBeanType().isAnnotationPresent(RawBody.class);
    }
}
