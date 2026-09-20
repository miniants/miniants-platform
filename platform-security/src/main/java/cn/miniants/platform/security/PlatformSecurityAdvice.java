package cn.miniants.platform.security;

import cn.miniants.platform.core.api.ApiResult;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class PlatformSecurityAdvice {

    private static final Logger log = LoggerFactory.getLogger(PlatformSecurityAdvice.class);

    @ExceptionHandler(AuthDeniedException.class)
    public ApiResult<Object> handleDenied(AuthDeniedException ex, HttpServletResponse response) {
        log.warn("[pl.security] {} -> {}", ex.errorCode().key(), ex.getMessage());
        response.setStatus(ex.status().value());
        if (ex.messageOverridden()) {
            return ApiResult.result(null, ex.errorCode().code(), ex.getMessage());
        }
        return ApiResult.failed(ex.errorCode());
    }

    @ExceptionHandler(UnboundAccountException.class)
    public ApiResult<Object> handleUnbound(UnboundAccountException ex, HttpServletResponse response) {
        log.info("[pl.security] {} -> {}", ex.errorCode().key(), ex.getMessage());
        response.setStatus(HttpStatus.OK.value());
        return ApiResult.result(null, ex.errorCode().code(), ex.getMessage());
    }
}
