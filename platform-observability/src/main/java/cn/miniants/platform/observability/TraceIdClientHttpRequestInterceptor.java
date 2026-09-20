package cn.miniants.platform.observability;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * 出站 HTTP 带上当前 MDC 的 {@code X-Trace-Id}。
 */
public class TraceIdClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        TraceIds.current().ifPresent(traceId -> request.getHeaders().set(TraceIds.HEADER_TRACE_ID, traceId));
        return execution.execute(request, body);
    }
}
