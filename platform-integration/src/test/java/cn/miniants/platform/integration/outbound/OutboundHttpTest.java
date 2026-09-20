package cn.miniants.platform.integration.outbound;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboundHttpTest {

    private HttpServer server;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/echo", exchange -> {
            byte[] body = "ok".getBytes();
            exchange.getResponseHeaders().add("X-Echo-Trace", exchange.getRequestHeaders().getFirst("X-Trace-Id"));
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
        MDC.clear();
    }

    @Test
    void postsAndCarriesTraceId() {
        MDC.put("traceId", "a1b2c3d4e5f60708");
        AtomicReference<String> echoed = new AtomicReference<>();
        server.createContext("/trace", exchange -> {
            echoed.set(exchange.getRequestHeaders().getFirst("X-Trace-Id"));
            byte[] body = "ok".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/trace?token=secret";
        OutboundResponse response = OutboundHttp.post("demo", "probe", url, "text/plain", "hi", Duration.ofSeconds(2));
        assertEquals(200, response.status());
        assertEquals("ok", response.body());
        assertTrue(response.ok());
        assertEquals("a1b2c3d4e5f60708", echoed.get());
    }
}
