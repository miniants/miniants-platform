package cn.miniants.platform.core.advice;

import cn.miniants.platform.core.api.ApiResult;
import cn.miniants.platform.core.error.ErrorMessages;
import cn.miniants.platform.core.error.PlatformCodes;
import cn.miniants.platform.core.error.PlatformException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 未识别异常不回传内部 message。宿主打开 {@code platform.core.web} 后覆盖全部 Controller。
 */
@RestControllerAdvice
public class PlatformWebAdvice {

    private static final Logger log = LoggerFactory.getLogger(PlatformWebAdvice.class);

    /** 与 {@code AccessLog.ATTR_ERR} 对齐；core 不依赖 observability。 */
    static final String ACCESS_ERR = "platform.access.err";

    private final ErrorMessages errorMessages;

    public PlatformWebAdvice(ErrorMessages errorMessages) {
        this.errorMessages = errorMessages;
    }

    @ExceptionHandler(PlatformException.class)
    public ApiResult<Object> handlePlatform(PlatformException ex, HttpServletResponse response) {
        log.warn("[pl] {} -> {}", ex.errorCode().key(), ex.getMessage());
        response.setStatus(HttpStatus.OK.value());
        String message = ex.messageOverridden() ? ex.getMessage() : errorMessages.resolve(ex.errorCode());
        return ApiResult.result(null, ex.errorCode().code(), message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResult<Object> handleBadRequest(IllegalArgumentException ex, HttpServletResponse response) {
        log.warn("[pl] bad-request -> {}", ex.getMessage());
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        return ApiResult.failed(errorMessages.resolve(PlatformCodes.BAD_REQUEST));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResult<Object> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletResponse response) {
        log.warn("[pl] type-mismatch -> {}", ex.getMessage());
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        return ApiResult.failed("参数格式不正确");
    }

    @ExceptionHandler({ServletRequestBindingException.class, HttpMessageNotReadableException.class})
    public ApiResult<Object> handleMissingRequestValue(
            Exception ex,
            HttpServletRequest request,
            HttpServletResponse response) {
        stampAccessErr(request, ex);
        log.warn("[pl] bad-request {} {} err={}", request.getMethod(), request.getRequestURI(),
                ex.getClass().getSimpleName());
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        return ApiResult.failed(errorMessages.resolve(PlatformCodes.BAD_REQUEST));
    }

    @ExceptionHandler(BindException.class)
    public ApiResult<Object> handleBind(BindException ex, HttpServletResponse response) {
        log.warn("[pl] bind -> {}", ex.getMessage());
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        return ApiResult.failed(fieldErrorsMessage(ex.getBindingResult().getFieldErrors()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResult<Object> handleConstraint(ConstraintViolationException ex, HttpServletResponse response) {
        log.warn("[pl] constraint -> {}", ex.getMessage());
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        return ApiResult.failed(constraintMessage(ex));
    }

    @ExceptionHandler({SQLIntegrityConstraintViolationException.class})
    public ApiResult<Object> handleDuplicate(Exception ex, HttpServletResponse response) {
        log.warn("[pl] duplicate -> {}", ex.getMessage());
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        return ApiResult.failed("数据重复");
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ApiResult<Object> handleNotFound(Exception ex, HttpServletRequest request, HttpServletResponse response) {
        stampAccessErr(request, ex);
        log.warn("[pl] not-found {} {} err={}", request.getMethod(), request.getRequestURI(),
                ex.getClass().getSimpleName());
        response.setStatus(HttpStatus.NOT_FOUND.value());
        return ApiResult.failed("资源不存在");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ApiResult<Object> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request,
            HttpServletResponse response) {
        stampAccessErr(request, ex);
        log.warn("[pl] method-not-allowed {} {} err={}", request.getMethod(), request.getRequestURI(),
                ex.getClass().getSimpleName());
        response.setStatus(HttpStatus.METHOD_NOT_ALLOWED.value());
        if (ex.getSupportedHttpMethods() != null && !ex.getSupportedHttpMethods().isEmpty()) {
            response.setHeader(HttpHeaders.ALLOW, ex.getSupportedHttpMethods().stream()
                    .map(HttpMethod::name)
                    .collect(Collectors.joining(", ")));
        }
        return ApiResult.failed("不支持的请求方法");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ApiResult<Object> handleResponseStatus(
            ResponseStatusException ex,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (!ex.getStatusCode().is4xxClientError()) {
            return handleUnknown(ex, request, response);
        }
        stampAccessErr(request, ex);
        int status = ex.getStatusCode().value();
        log.warn("[pl] client-status {} {} status={} err={}", request.getMethod(), request.getRequestURI(),
                status, ex.getClass().getSimpleName());
        response.setStatus(status);
        return ApiResult.failed(clientStatusMessage(status));
    }

    @ExceptionHandler(Exception.class)
    public ApiResult<Object> handleUnknown(Exception ex, HttpServletRequest request, HttpServletResponse response) {
        Throwable error = unwrap(ex);
        stampAccessErr(request, error);
        if (response == null || isBenignClientDisconnect(error, response)) {
            if (response != null && !response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            }
            if (response == null) {
                log.warn("[pl] client-abort {} {} -> {}", request.getMethod(), request.getRequestURI(),
                        error.getMessage());
            } else {
                log.debug("[pl] client-disconnect {} {} -> {}", request.getMethod(), request.getRequestURI(),
                        error.getMessage());
            }
            return null;
        }
        if (isDuplicateKey(error)) {
            return handleDuplicate((Exception) error, response);
        }
        if (looksLikeInfraReset(error)) {
            log.warn("[pl] infra {} {} -> {}", request.getMethod(), request.getRequestURI(), error.toString());
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return ApiResult.failed(errorMessages.resolve(PlatformCodes.INTERNAL));
        }
        if (error instanceof ErrorResponse er && er.getStatusCode().is4xxClientError()) {
            return writeClientError(request, response, error, er.getStatusCode().value());
        }
        if (error instanceof MultipartException) {
            return writeClientError(request, response, error, HttpStatus.BAD_REQUEST.value());
        }
        log.error("[pl] internal {} {} err={}", request.getMethod(), request.getRequestURI(),
                error.getClass().getSimpleName(), error);
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        return ApiResult.failed(withTrace(errorMessages.resolve(PlatformCodes.INTERNAL)));
    }

    private ApiResult<Object> writeClientError(
            HttpServletRequest request, HttpServletResponse response, Throwable error, int status) {
        log.warn("[pl] client-status {} {} status={} err={}", request.getMethod(), request.getRequestURI(),
                status, error.getClass().getSimpleName());
        response.setStatus(status);
        return ApiResult.failed(clientStatusMessage(status));
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

    static void stampAccessErr(HttpServletRequest request, Throwable error) {
        if (request == null || error == null) {
            return;
        }
        request.setAttribute(ACCESS_ERR, error.getClass().getSimpleName());
    }

    public static String withTrace(String brief) {
        String text = brief == null || brief.isBlank() ? PlatformCodes.INTERNAL.defaultMessage() : brief.trim();
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isBlank() || text.contains(traceId)) {
            return text;
        }
        return text + "（traceId=" + traceId.trim() + "）";
    }

    public static boolean isClientAbort(Throwable ex) {
        return looksLikeClientDisconnect(ex);
    }

    static boolean isBenignClientDisconnect(Throwable ex, HttpServletResponse response) {
        if (response != null && response.isCommitted()) {
            return true;
        }
        return looksLikeClientDisconnect(ex);
    }

    public static boolean looksLikeClientDisconnect(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if ("org.apache.catalina.connector.ClientAbortException".equals(current.getClass().getName())) {
                return true;
            }
            String message = current.getMessage() == null ? "" : current.getMessage().toLowerCase();
            if (message.contains("broken pipe")
                    || message.contains("connection reset by peer")
                    || message.contains("failed to flushbuffer")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public static boolean looksLikeInfraReset(Throwable ex) {
        if (looksLikeClientDisconnect(ex)) {
            return false;
        }
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage() == null ? "" : current.getMessage().toLowerCase();
            if (message.contains("connection reset")) {
                return true;
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

    private static Throwable unwrap(Throwable ex) {
        if (ex != null && ex.getCause() != null
                && "org.springframework.web.util.NestedServletException".equals(ex.getClass().getName())) {
            return ex.getCause();
        }
        return ex;
    }
}
