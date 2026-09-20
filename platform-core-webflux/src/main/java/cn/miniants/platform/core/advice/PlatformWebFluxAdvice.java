package cn.miniants.platform.core.advice;

import cn.miniants.platform.core.api.ApiResult;
import cn.miniants.platform.core.error.ErrorMessages;
import cn.miniants.platform.core.error.PlatformCodes;
import cn.miniants.platform.core.error.PlatformException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.reactive.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * WebFlux 异常适配：与 MVC {@link PlatformWebAdvice} 语义对齐（业务 PlatformException 仍 HTTP 200）。
 */
@RestControllerAdvice
@Order(-100)
public class PlatformWebFluxAdvice {

    private static final Logger log = LoggerFactory.getLogger(PlatformWebFluxAdvice.class);

    static final String ACCESS_ERR = "platform.access.err";

    private final ErrorMessages errorMessages;

    public PlatformWebFluxAdvice(ErrorMessages errorMessages) {
        this.errorMessages = errorMessages;
    }

    @ExceptionHandler(PlatformException.class)
    public ResponseEntity<ApiResult<Object>> handlePlatform(PlatformException ex) {
        log.warn("[pl] {} -> {}", ex.errorCode().key(), ex.getMessage());
        String message = ex.messageOverridden() ? ex.getMessage() : errorMessages.resolve(ex.errorCode());
        return ResponseEntity.ok(ApiResult.result(null, ex.errorCode().code(), message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResult<Object>> handleBadRequest(IllegalArgumentException ex) {
        log.warn("[pl] bad-request -> {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResult.failed(errorMessages.resolve(PlatformCodes.BAD_REQUEST)));
    }

    @ExceptionHandler({BindException.class, WebExchangeBindException.class})
    public ResponseEntity<ApiResult<Object>> handleBind(Exception ex) {
        log.warn("[pl] bind -> {}", ex.getMessage());
        List<FieldError> fieldErrors;
        if (ex instanceof BindException bind) {
            fieldErrors = bind.getBindingResult().getFieldErrors();
        } else {
            fieldErrors = ((WebExchangeBindException) ex).getBindingResult().getFieldErrors();
        }
        return ResponseEntity.badRequest().body(ApiResult.failed(fieldErrorsMessage(fieldErrors)));
    }

    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
    public ResponseEntity<ApiResult<Object>> handleConstraint(Exception ex) {
        log.warn("[pl] constraint -> {}", ex.getMessage());
        String message = ex instanceof ConstraintViolationException cve
                ? constraintMessage(cve)
                : "请求参数不正确";
        return ResponseEntity.badRequest().body(ApiResult.failed(message));
    }

    @ExceptionHandler(SQLIntegrityConstraintViolationException.class)
    public ResponseEntity<ApiResult<Object>> handleDuplicate(Exception ex) {
        log.warn("[pl] duplicate -> {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ApiResult.failed("数据重复"));
    }

    @ExceptionHandler({NoResourceFoundException.class, ResponseStatusException.class})
    public ResponseEntity<ApiResult<Object>> handleNotFound(Exception ex, ServerWebExchange exchange) {
        if (ex instanceof ResponseStatusException rse && rse.getStatusCode().value() != 404) {
            if (rse.getStatusCode().is4xxClientError()) {
                return handleClientStatus(rse, exchange);
            }
            return handleUnknown(ex, exchange);
        }
        stampAccessErr(exchange, ex);
        log.warn("[pl] not-found {} {} err={}", exchange.getRequest().getMethod(),
                exchange.getRequest().getURI().getPath(), ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResult.failed("资源不存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Object>> handleUnknown(Exception ex, ServerWebExchange exchange) {
        stampAccessErr(exchange, ex);
        if (isDuplicateKey(ex)) {
            return handleDuplicate(ex);
        }
        if (looksLikeInfraReset(ex)) {
            log.warn("[pl] infra {} {} -> {}", exchange.getRequest().getMethod(),
                    exchange.getRequest().getURI().getPath(), ex.toString());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResult.failed(errorMessages.resolve(PlatformCodes.INTERNAL)));
        }
        if (ex instanceof ErrorResponse er && er.getStatusCode().is4xxClientError()) {
            return writeClientError(exchange, ex, er.getStatusCode().value());
        }
        log.error("[pl] internal {} {} err={}", exchange.getRequest().getMethod(),
                exchange.getRequest().getURI().getPath(), ex.getClass().getSimpleName(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResult.failed(withTrace(errorMessages.resolve(PlatformCodes.INTERNAL))));
    }

    private ResponseEntity<ApiResult<Object>> handleClientStatus(
            ResponseStatusException ex, ServerWebExchange exchange) {
        return writeClientError(exchange, ex, ex.getStatusCode().value());
    }

    private ResponseEntity<ApiResult<Object>> writeClientError(
            ServerWebExchange exchange, Exception ex, int status) {
        stampAccessErr(exchange, ex);
        log.warn("[pl] client-status {} {} status={} err={}", exchange.getRequest().getMethod(),
                exchange.getRequest().getURI().getPath(), status, ex.getClass().getSimpleName());
        return ResponseEntity.status(status).body(ApiResult.failed(clientStatusMessage(status)));
    }

    static String clientStatusMessage(int status) {
        return switch (status) {
            case 400 -> "请求参数不正确";
            case 404 -> "资源不存在";
            case 405 -> "不支持的请求方法";
            case 406 -> "不支持的响应类型";
            case 413 -> "上传文件过大";
            case 415 -> "不支持的请求类型";
            default -> "请求无法处理";
        };
    }

    static void stampAccessErr(ServerWebExchange exchange, Throwable error) {
        if (exchange == null || error == null) {
            return;
        }
        exchange.getAttributes().put(ACCESS_ERR, error.getClass().getSimpleName());
    }

    public static String withTrace(String brief) {
        String text = brief == null || brief.isBlank() ? PlatformCodes.INTERNAL.defaultMessage() : brief.trim();
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isBlank() || text.contains(traceId)) {
            return text;
        }
        return text + "（traceId=" + traceId.trim() + "）";
    }

    public static boolean looksLikeInfraReset(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage() == null ? "" : current.getMessage().toLowerCase();
            if (message.contains("connection reset") && !message.contains("connection reset by peer")) {
                return true;
            }
            if (message.contains("connection reset") && message.contains("broken pipe")) {
                return false;
            }
            current = current.getCause();
        }
        return false;
    }

    static boolean isDuplicateKey(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof SQLIntegrityConstraintViolationException
                    || "org.springframework.dao.DuplicateKeyException".equals(current.getClass().getName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public static String fieldErrorsMessage(List<FieldError> fieldErrors) {
        if (fieldErrors == null || fieldErrors.isEmpty()) {
            return "请求参数不正确";
        }
        return fieldErrors.stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
    }

    static String constraintMessage(ConstraintViolationException exception) {
        if (exception.getConstraintViolations() == null || exception.getConstraintViolations().isEmpty()) {
            return "请求参数不正确";
        }
        return exception.getConstraintViolations().stream().map(violation -> {
            String path = violation.getPropertyPath().toString();
            int dot = path.lastIndexOf('.');
            String name = dot < 0 ? path : path.substring(dot + 1);
            return name + " " + violation.getMessage();
        }).collect(Collectors.joining(", "));
    }
}
