package cn.miniants.platform.core.advice;

import cn.miniants.platform.core.api.ApiResult;
import cn.miniants.platform.core.error.ErrorMessages;
import cn.miniants.platform.core.error.PlatformCodes;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.server.ServerWebInputException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PlatformWebFluxAdviceTest {

    @Test
    void missingRequestValueIsBadRequestNotInternalError() {
        PlatformWebFluxAdvice advice = new PlatformWebFluxAdvice(errorMessages());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/open/web/password"));
        ResponseEntity<ApiResult<Object>> result = advice.handleNotFound(
                new ServerWebInputException("Required query parameter 'username' is not present"),
                exchange);
        assertEquals(400, result.getStatusCode().value());
        assertNotNull(result.getBody());
        assertEquals(PlatformCodes.BAD_REQUEST.defaultMessage(), result.getBody().getMessage());
        assertFalse(result.getBody().getMessage().contains("username"));
        assertEquals("ServerWebInputException", exchange.getAttribute(PlatformWebFluxAdvice.ACCESS_ERR));
    }

    @Test
    void errorResponse4xxIsNotInternalError() {
        PlatformWebFluxAdvice advice = new PlatformWebFluxAdvice(errorMessages());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/open/web/password"));
        ResponseEntity<ApiResult<Object>> result = advice.handleUnknown(
                new ErrorResponseException(HttpStatus.UNSUPPORTED_MEDIA_TYPE), exchange);
        assertEquals(415, result.getStatusCode().value());
        assertNotNull(result.getBody());
        assertEquals("不支持的请求类型", result.getBody().getMessage());
        assertEquals("ErrorResponseException", exchange.getAttribute(PlatformWebFluxAdvice.ACCESS_ERR));
    }

    private static ErrorMessages errorMessages() {
        StaticMessageSource empty = new StaticMessageSource();
        empty.setUseCodeAsDefaultMessage(false);
        return new ErrorMessages(empty, empty);
    }
}
