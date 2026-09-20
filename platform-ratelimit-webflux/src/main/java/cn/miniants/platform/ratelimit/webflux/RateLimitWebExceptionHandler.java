package cn.miniants.platform.ratelimit.webflux;

import cn.miniants.platform.core.api.ApiResult;
import cn.miniants.platform.ratelimit.RateLimitExceededException;
import cn.miniants.platform.ratelimit.support.RateLimitHttpHeaders;
import tools.jackson.databind.ObjectMapper;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;

/**
 * WebFilter 抛出的限流异常不走 {@code @RestControllerAdvice}，由此处理器写 429。
 */
public class RateLimitWebExceptionHandler implements WebExceptionHandler, Ordered {

    private final ObjectMapper objectMapper;

    public RateLimitWebExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        RateLimitExceededException rateLimit = find(ex);
        if (rateLimit == null) {
            return Mono.error(ex);
        }
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(rateLimit);
        }
        exchange.getAttributes().put(RateLimitHttpHeaders.ACCESS_ERR_ATTR, "RateLimitExceeded");
        if (rateLimit.decision() != null) {
            exchange.getAttributes().put(
                    RateLimitHttpHeaders.ACCESS_REASON_ATTR,
                    "rate-limit:" + rateLimit.policyCode() + ":" + rateLimit.decision().outcome());
        }
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        for (Map.Entry<String, String> header : RateLimitHttpHeaders.from(rateLimit).entrySet()) {
            exchange.getResponse().getHeaders().set(header.getKey(), header.getValue());
        }
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String message = rateLimit.getMessage() == null || rateLimit.getMessage().isBlank()
                ? "请求过于频繁"
                : rateLimit.getMessage();
        try {
            byte[] json = objectMapper.writeValueAsBytes(ApiResult.result(null, -1L, message));
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(json);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception writeError) {
            return Mono.error(writeError);
        }
    }

    private static RateLimitExceededException find(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            if (cur instanceof RateLimitExceededException rateLimit) {
                return rateLimit;
            }
            cur = cur.getCause();
        }
        return null;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
