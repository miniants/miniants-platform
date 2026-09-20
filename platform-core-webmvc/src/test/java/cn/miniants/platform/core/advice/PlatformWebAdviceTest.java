package cn.miniants.platform.core.advice;

import cn.miniants.platform.core.api.ApiResult;
import cn.miniants.platform.core.error.ErrorMessages;
import cn.miniants.platform.core.error.PlatformCodes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformWebAdviceTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void unknownMessageAppendsTraceId() {
        MDC.put("traceId", "a1b2c3d4e5f60708");
        assertEquals("服务暂不可用，请稍后重试（traceId=a1b2c3d4e5f60708）",
                PlatformWebAdvice.withTrace("服务暂不可用，请稍后重试"));
    }

    @Test
    void redisResetIsServiceUnavailableNotClientAbort() {
        PlatformWebAdvice advice = new PlatformWebAdvice(errorMessages());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sys/user/page");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ApiResult<Object> result = advice.handleUnknown(
                new IOException("Connection reset"), request, response);
        assertEquals(503, response.getStatus());
        assertEquals(PlatformCodes.INTERNAL.defaultMessage(), result.getMessage());
        assertFalse(PlatformWebAdvice.isClientAbort(new IOException("Connection reset")));
        assertTrue(PlatformWebAdvice.looksLikeInfraReset(new IOException("Connection reset")));
    }

    @Test
    void unknownDoesNotLeakInternalText() {
        PlatformWebAdvice advice = new PlatformWebAdvice(errorMessages());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sys/user/page");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ApiResult<Object> result = advice.handleUnknown(
                new ClassCastException("Service Error: boom"), request, response);
        assertEquals(503, response.getStatus());
        assertTrue(result.getMessage().startsWith(PlatformCodes.INTERNAL.defaultMessage()));
        assertFalse(result.getMessage().contains("ClassCastException"));
        assertFalse(result.getMessage().contains("Service Error"));
        assertEquals("ClassCastException", request.getAttribute(PlatformWebAdvice.ACCESS_ERR));
    }

    @Test
    void missingStaticResourceIsNotFound() {
        PlatformWebAdvice advice = new PlatformWebAdvice(errorMessages());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/jwy-shared-iot/iot/iot-config/apply/tch");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ApiResult<Object> result = advice.handleNotFound(
                new NoResourceFoundException(HttpMethod.GET, "/", "jwy-shared-iot/iot/iot-config/apply/tch"),
                request, response);
        assertEquals(404, response.getStatus());
        assertEquals("资源不存在", result.getMessage());
        assertEquals("NoResourceFoundException", request.getAttribute(PlatformWebAdvice.ACCESS_ERR));
    }

    @Test
    void missingRequestParameterIsBadRequestNotInternalError() {
        PlatformWebAdvice advice = new PlatformWebAdvice(errorMessages());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/open/web/password");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ApiResult<Object> result = advice.handleMissingRequestValue(
                new MissingServletRequestParameterException("username", "String"),
                request, response);
        assertEquals(400, response.getStatus());
        assertEquals(PlatformCodes.BAD_REQUEST.defaultMessage(), result.getMessage());
        assertFalse(result.getMessage().contains("username"));
        assertEquals("MissingServletRequestParameterException", request.getAttribute(PlatformWebAdvice.ACCESS_ERR));
    }

    @Test
    void unreadableBodyIsBadRequestNotInternalError() {
        PlatformWebAdvice advice = new PlatformWebAdvice(errorMessages());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/open/web/password");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ApiResult<Object> result = advice.handleMissingRequestValue(
                new HttpMessageNotReadableException("JSON parse error", new MockHttpInputMessage(new byte[0])),
                request, response);
        assertEquals(400, response.getStatus());
        assertEquals(PlatformCodes.BAD_REQUEST.defaultMessage(), result.getMessage());
        assertFalse(result.getMessage().contains("JSON parse error"));
        assertEquals("HttpMessageNotReadableException", request.getAttribute(PlatformWebAdvice.ACCESS_ERR));
    }

    @Test
    void missingMultipartPartIsBadRequestNotInternalError() {
        PlatformWebAdvice advice = new PlatformWebAdvice(errorMessages());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/register/student/register-student/photo");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ApiResult<Object> result = advice.handleUnknown(
                new MissingServletRequestPartException("file"), request, response);
        assertEquals(400, response.getStatus());
        assertEquals("请求参数不正确", result.getMessage());
        assertEquals("MissingServletRequestPartException", request.getAttribute(PlatformWebAdvice.ACCESS_ERR));
    }

    @Test
    void unsupportedMediaTypeIsNotInternalError() {
        PlatformWebAdvice advice = new PlatformWebAdvice(errorMessages());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/open/web/password");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ApiResult<Object> result = advice.handleUnknown(
                new HttpMediaTypeNotSupportedException(
                        MediaType.APPLICATION_JSON, List.of(MediaType.APPLICATION_FORM_URLENCODED)),
                request, response);
        assertEquals(415, response.getStatus());
        assertEquals("不支持的请求类型", result.getMessage());
        assertEquals("HttpMediaTypeNotSupportedException", request.getAttribute(PlatformWebAdvice.ACCESS_ERR));
    }

    @Test
    void uploadTooLargeIsNotInternalError() {
        PlatformWebAdvice advice = new PlatformWebAdvice(errorMessages());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/integration/oss/upload");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ApiResult<Object> result = advice.handleUnknown(
                new MaxUploadSizeExceededException(1024), request, response);
        assertEquals(413, response.getStatus());
        assertEquals("上传文件过大", result.getMessage());
        assertEquals("MaxUploadSizeExceededException", request.getAttribute(PlatformWebAdvice.ACCESS_ERR));
    }

    @Test
    void notMultipartIsBadRequestNotInternalError() {
        PlatformWebAdvice advice = new PlatformWebAdvice(errorMessages());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/register/student/register-student/photo");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ApiResult<Object> result = advice.handleUnknown(
                new MultipartException("Current request is not a multipart request"), request, response);
        assertEquals(400, response.getStatus());
        assertEquals("请求参数不正确", result.getMessage());
        assertFalse(result.getMessage().contains("multipart"));
        assertEquals("MultipartException", request.getAttribute(PlatformWebAdvice.ACCESS_ERR));
    }

    @Test
    void wrongHttpMethodIsNotInternalError() {
        PlatformWebAdvice advice = new PlatformWebAdvice(errorMessages());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/open/web/login-challenge");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ApiResult<Object> result = advice.handleMethodNotAllowed(
                new HttpRequestMethodNotSupportedException("POST", List.of("GET")),
                request, response);
        assertEquals(405, response.getStatus());
        assertEquals("GET", response.getHeader(HttpHeaders.ALLOW));
        assertEquals("不支持的请求方法", result.getMessage());
        assertEquals("HttpRequestMethodNotSupportedException", request.getAttribute(PlatformWebAdvice.ACCESS_ERR));
    }

    @Test
    void clientResponseStatusKeepsStatus() {
        PlatformWebAdvice advice = new PlatformWebAdvice(errorMessages());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/school/admin/student-pics/photo");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ApiResult<Object> result = advice.handleResponseStatus(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在"),
                request, response);
        assertEquals(404, response.getStatus());
        assertEquals("资源不存在", result.getMessage());
        assertFalse(result.getMessage().contains("文件不存在"));
        assertEquals("ResponseStatusException", request.getAttribute(PlatformWebAdvice.ACCESS_ERR));
    }

    @Test
    void missingHandlerIsNotFound() {
        PlatformWebAdvice advice = new PlatformWebAdvice(errorMessages());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/jwy-service-auth/oauth/token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ApiResult<Object> result = advice.handleNotFound(
                new NoHandlerFoundException("POST", "/jwy-service-auth/oauth/token", HttpHeaders.EMPTY),
                request, response);
        assertEquals(404, response.getStatus());
        assertEquals("NoHandlerFoundException", request.getAttribute(PlatformWebAdvice.ACCESS_ERR));
    }

    @Test
    void brokenPipeIsClientDisconnect() {
        assertTrue(PlatformWebAdvice.looksLikeClientDisconnect(
                new IOException("ServletResponse failed to flushBuffer: java.io.IOException: Broken pipe")));
    }

    @Test
    void committedResponseSkipsBody() {
        PlatformWebAdvice advice = new PlatformWebAdvice(errorMessages());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sys/user/page");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCommitted(true);
        assertNull(advice.handleUnknown(new IllegalStateException("gone"), request, response));
    }

    private static ErrorMessages errorMessages() {
        StaticMessageSource empty = new StaticMessageSource();
        empty.setUseCodeAsDefaultMessage(false);
        return new ErrorMessages(empty, empty);
    }
}
