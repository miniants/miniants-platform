package cn.miniants.platform.ratelimit.admin.web;

import cn.miniants.platform.core.api.ApiResult;
import cn.miniants.platform.ratelimit.admin.RateLimitVersionConflictException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = RateLimitPolicyAdminController.class)
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitAdminAdvice {

    @ExceptionHandler(RateLimitVersionConflictException.class)
    public ResponseEntity<ApiResult<Object>> conflict(RateLimitVersionConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResult.result(ex.current(), -1L, ex.getMessage()));
    }
}
