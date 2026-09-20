package cn.miniants.platform.ratelimit.webflux;

import cn.miniants.platform.core.api.ApiResult;
import cn.miniants.platform.ratelimit.RateLimitExceededException;
import cn.miniants.platform.ratelimit.support.RateLimitHttpHeaders;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

import java.util.Map;

/**
 * WebFlux 限流拒绝 → HTTP 429，优先于 {@code PlatformWebFluxAdvice} 的 200 包装。
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitWebFluxExceededAdvice {

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResult<Object>> handle(RateLimitExceededException ex, ServerWebExchange exchange) {
        if (exchange != null) {
            exchange.getAttributes().put(RateLimitHttpHeaders.ACCESS_ERR_ATTR, "RateLimitExceeded");
            if (ex.decision() != null) {
                exchange.getAttributes().put(
                        RateLimitHttpHeaders.ACCESS_REASON_ATTR,
                        "rate-limit:" + ex.policyCode() + ":" + ex.decision().outcome());
            }
        }
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS);
        for (Map.Entry<String, String> header : RateLimitHttpHeaders.from(ex).entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }
        String message = ex.getMessage() == null || ex.getMessage().isBlank()
                ? "请求过于频繁"
                : ex.getMessage();
        return builder.body(ApiResult.result(null, -1L, message));
    }
}
