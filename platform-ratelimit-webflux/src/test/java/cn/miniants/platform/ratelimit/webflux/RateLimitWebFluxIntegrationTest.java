package cn.miniants.platform.ratelimit.webflux;

import cn.miniants.platform.core.api.ApiResult;
import cn.miniants.platform.ratelimit.annotation.RateLimit;
import cn.miniants.platform.ratelimit.config.PlatformRateLimitCoreAutoConfiguration;
import cn.miniants.platform.ratelimit.webflux.config.PlatformRateLimitWebFluxAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.codec.autoconfigure.CodecsAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.webflux.autoconfigure.HttpHandlerAutoConfiguration;
import org.springframework.boot.webflux.autoconfigure.WebFluxAutoConfiguration;
import org.springframework.boot.webflux.autoconfigure.error.ErrorWebFluxAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitWebFluxIntegrationTest {

    private final ReactiveWebApplicationContextRunner runner = new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    CodecsAutoConfiguration.class,
                    WebFluxAutoConfiguration.class,
                    HttpHandlerAutoConfiguration.class,
                    ErrorWebFluxAutoConfiguration.class,
                    PlatformRateLimitCoreAutoConfiguration.class,
                    PlatformRateLimitWebFluxAutoConfiguration.class))
            .withUserConfiguration(TestControllers.class)
            .withPropertyValues(
                    "platform.ratelimit.enabled=true",
                    "platform.ratelimit.backend=local",
                    "platform.ratelimit.policies[api.ping].limit=2",
                    "platform.ratelimit.policies[api.ping].period=1m",
                    "platform.ratelimit.policies[api.ping].algorithm=SLIDING_WINDOW");

    @Test
    void allowsThenDeniesWith429Headers() {
        runner.run(context -> {
            WebTestClient client = WebTestClient.bindToApplicationContext(context).build();
            client.get().uri("/ping").accept(MediaType.APPLICATION_JSON).exchange()
                    .expectStatus().isOk();
            client.get().uri("/ping").accept(MediaType.APPLICATION_JSON).exchange()
                    .expectStatus().isOk();
            client.get().uri("/ping").accept(MediaType.APPLICATION_JSON).exchange()
                    .expectStatus().isEqualTo(429)
                    .expectHeader().exists("Retry-After")
                    .expectHeader().valueEquals("RateLimit-Limit", "2")
                    .expectHeader().exists("RateLimit-Remaining")
                    .expectHeader().exists("RateLimit-Reset")
                    .expectBody()
                    .jsonPath("$.code").isEqualTo(-1)
                    .jsonPath("$.message").isNotEmpty();
        });
    }

    @Test
    void forgedXffIgnoredWhenProxyNotTrusted() {
        runner.withUserConfiguration(InlineControllerConfig.class)
                .run(context -> {
                    WebTestClient client = WebTestClient.bindToApplicationContext(context).build();
                    for (int i = 0; i < 3; i++) {
                        var spec = client.get().uri("/inline")
                                .header("X-Forwarded-For", "8.8.8.8")
                                .accept(MediaType.APPLICATION_JSON)
                                .exchange();
                        if (i < 2) {
                            spec.expectStatus().isOk();
                        } else {
                            spec.expectStatus().isEqualTo(429);
                        }
                    }
                });
    }

    @Test
    void functionalRouteFilterReturns429() {
        runner.withUserConfiguration(FunctionalRouteConfig.class)
                .run(context -> {
                    WebTestClient client = WebTestClient.bindToApplicationContext(context).build();
                    client.get().uri("/fn").accept(MediaType.APPLICATION_JSON).exchange()
                            .expectStatus().isOk();
                    client.get().uri("/fn").accept(MediaType.APPLICATION_JSON).exchange()
                            .expectStatus().isEqualTo(429)
                            .expectHeader().exists("Retry-After")
                            .expectBody()
                            .jsonPath("$.code").isEqualTo(-1);
                });
    }

    @Test
    void startupFailsWhenNamedPolicyMissing() {
        new ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        JacksonAutoConfiguration.class,
                        CodecsAutoConfiguration.class,
                        WebFluxAutoConfiguration.class,
                        HttpHandlerAutoConfiguration.class,
                        PlatformRateLimitCoreAutoConfiguration.class,
                        PlatformRateLimitWebFluxAutoConfiguration.class))
                .withUserConfiguration(MissingPolicyConfig.class)
                .withPropertyValues("platform.ratelimit.backend=local")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootMessage(context.getStartupFailure())).contains("未找到限流策略");
                });
    }

    private static String rootMessage(Throwable failure) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = failure;
        while (cur != null) {
            if (cur.getMessage() != null) {
                sb.append(cur.getMessage()).append('\n');
            }
            cur = cur.getCause();
        }
        return sb.toString();
    }

    @Configuration
    static class TestControllers {
        @Bean
        PingController pingController() {
            return new PingController();
        }
    }

    @RestController
    static class PingController {
        @GetMapping("/ping")
        @RateLimit(policy = "api.ping")
        ApiResult<String> ping() {
            return ApiResult.ok("ok");
        }
    }

    @Configuration
    static class InlineControllerConfig {
        @Bean
        InlineController inlineController() {
            return new InlineController();
        }
    }

    @RestController
    static class InlineController {
        @GetMapping("/inline")
        @RateLimit(limit = 2, period = "1m", algorithm = cn.miniants.platform.ratelimit.RateLimitAlgorithm.SLIDING_WINDOW)
        ApiResult<String> inline() {
            return ApiResult.ok("ok");
        }
    }

    @RateLimit(limit = 1, period = "1m", algorithm = cn.miniants.platform.ratelimit.RateLimitAlgorithm.SLIDING_WINDOW)
    static class FunctionalLimitHolder {
    }

    @Configuration
    static class FunctionalRouteConfig {
        @Bean
        RouterFunction<ServerResponse> functionalRoute(RateLimitWebFilter webFilter) {
            return RouterFunctions.route(RequestPredicates.GET("/fn"), request ->
                            ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(ApiResult.ok("ok")))
                    .filter(RateLimitHandlerFilterFunction.of(
                            webFilter,
                            RateLimitHandlerFilterFunction.bindingsFromElement(new FunctionalLimitHolder())));
        }
    }

    @Configuration
    static class MissingPolicyConfig {
        @Bean
        MissingController missingController() {
            return new MissingController();
        }
    }

    @RestController
    static class MissingController {
        @GetMapping("/missing")
        @RateLimit(policy = "does.not.exist")
        ApiResult<String> missing() {
            return ApiResult.ok("ok");
        }
    }
}
