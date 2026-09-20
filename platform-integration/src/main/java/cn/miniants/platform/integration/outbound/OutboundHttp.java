package cn.miniants.platform.integration.outbound;

import org.slf4j.MDC;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * JDK HttpClient 出站。打 {@code pl.outbound}，不读、不记录对端 body 到日志。
 */
public final class OutboundHttp {

    private static final String TRACE_HEADER = "X-Trace-Id";
    private static final HttpClient CLIENT = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    private OutboundHttp() {
    }

    public static OutboundResponse post(
            String peer, String biz, String url, String contentType, String body, Duration timeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout == null ? Duration.ofSeconds(10) : timeout)
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body, StandardCharsets.UTF_8));
        if (contentType != null && !contentType.isBlank()) {
            builder.header("Content-Type", contentType);
        }
        return execute(peer, biz, builder);
    }

    public static OutboundResponse get(String peer, String biz, String url, Duration timeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout == null ? Duration.ofSeconds(10) : timeout)
                .GET();
        return execute(peer, biz, builder);
    }

    public static OutboundResponse execute(String peer, String biz, HttpRequest.Builder builder) {
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            builder.header(TRACE_HEADER, traceId);
        }
        HttpRequest request = builder.build();
        String method = request.method() == null ? "-" : request.method();
        String path = OutboundLog.pathOf(request.uri().toString());
        long startNs = System.nanoTime();
        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            boolean ok = status >= 200 && status < 400;
            OutboundLog.emit(peer, status, elapsedMs(startNs), method, path, ok, biz, "-");
            return new OutboundResponse(status, response.body());
        } catch (IOException | InterruptedException | RuntimeException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            OutboundLog.emit(peer, 0, elapsedMs(startNs), method, path, false, biz, ex.getClass().getSimpleName());
            throw new IllegalStateException("出站调用失败", ex);
        }
    }

    private static long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }
}
