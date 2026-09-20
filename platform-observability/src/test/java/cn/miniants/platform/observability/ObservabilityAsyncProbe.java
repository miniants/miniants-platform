package cn.miniants.platform.observability;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
class ObservabilityAsyncProbe {

    @Async
    public CompletableFuture<String> currentTraceId() {
        return CompletableFuture.completedFuture(TraceIds.current().orElse(""));
    }
}
