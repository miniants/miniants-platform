package cn.miniants.platform.core.advice;

import cn.miniants.platform.core.api.ApiResult;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformWebFluxResultFilterTest {

    @Test
    void alreadyWrappedDetectsApiResult() {
        assertThat(PlatformWebFluxResultFilter.alreadyWrapped(ApiResult.ok("x"))).isTrue();
        assertThat(PlatformWebFluxResultFilter.alreadyWrapped(Mono.just("x"))).isFalse();
        assertThat(PlatformWebFluxResultFilter.alreadyWrapped(Flux.just("a", "b"))).isFalse();
    }

    @Test
    void fluxContractIsSingleListEnvelope() {
        List<String> collected = Flux.just("a", "b").collectList().block();
        ApiResult<List<String>> envelope = ApiResult.ok(collected);
        assertThat(envelope.getData()).containsExactly("a", "b");
        assertThat(envelope.getCode()).isEqualTo(200L);
    }
}
