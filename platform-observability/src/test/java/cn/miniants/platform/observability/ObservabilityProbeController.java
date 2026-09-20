package cn.miniants.platform.observability;

import cn.miniants.platform.security.PublicAccess;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@PublicAccess
class ObservabilityProbeController {

    private final ObservabilityAsyncProbe asyncProbe;

    ObservabilityProbeController(ObservabilityAsyncProbe asyncProbe) {
        this.asyncProbe = asyncProbe;
    }

    @GetMapping("/__obs/trace")
    Map<String, String> trace() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("traceId", TraceIds.current().orElse(""));
        body.put("uid", org.slf4j.MDC.get(TraceIds.MDC_UID));
        body.put("ip", org.slf4j.MDC.get(TraceIds.MDC_IP));
        return body;
    }

    @GetMapping("/__obs/async")
    Map<String, String> async() {
        String requestId = TraceIds.current().orElse("");
        CompletableFuture<String> future = asyncProbe.currentTraceId();
        Map<String, String> body = new LinkedHashMap<>();
        body.put("request", requestId);
        body.put("async", future.join());
        return body;
    }
}
