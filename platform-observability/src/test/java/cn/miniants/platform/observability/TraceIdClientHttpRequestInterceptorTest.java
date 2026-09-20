package cn.miniants.platform.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.OK;

class TraceIdClientHttpRequestInterceptorTest {

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void writesCurrentTraceId() throws Exception {
        TraceIds.bind("a1b2c3d4e5f60708");
        MockClientHttpRequest request = new MockClientHttpRequest(GET, URI.create("http://peer/x"));
        ClientHttpRequestExecution execution = (HttpRequest req, byte[] body) -> {
            assertEquals("a1b2c3d4e5f60708", req.getHeaders().getFirst(TraceIds.HEADER_TRACE_ID));
            return new MockClientHttpResponse(new byte[0], OK);
        };
        new TraceIdClientHttpRequestInterceptor().intercept(request, new byte[0], execution);
    }

    @Test
    void skipsWhenNoTraceId() throws Exception {
        MockClientHttpRequest request = new MockClientHttpRequest(GET, URI.create("http://peer/x"));
        ClientHttpRequestExecution execution = (HttpRequest req, byte[] body) -> {
            assertNull(req.getHeaders().getFirst(TraceIds.HEADER_TRACE_ID));
            return new MockClientHttpResponse(new byte[0], OK);
        };
        new TraceIdClientHttpRequestInterceptor().intercept(request, new byte[0], execution);
    }
}
