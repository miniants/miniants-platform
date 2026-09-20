package cn.miniants.platform.ratelimit.webmvc;

import cn.miniants.platform.core.api.ApiResult;
import cn.miniants.platform.ratelimit.annotation.RateLimit;
import cn.miniants.platform.ratelimit.config.PlatformRateLimitCoreAutoConfiguration;
import cn.miniants.platform.ratelimit.webmvc.config.PlatformRateLimitWebMvcAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RateLimitWebMvcIntegrationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    PlatformRateLimitCoreAutoConfiguration.class,
                    PlatformRateLimitWebMvcAutoConfiguration.class,
                    WebMvcAutoConfiguration.class,
                    DispatcherServletAutoConfiguration.class))
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
            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).build();
            mockMvc.perform(get("/ping").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/ping").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/ping").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(header().string("RateLimit-Limit", "2"))
                    .andExpect(header().exists("RateLimit-Remaining"))
                    .andExpect(header().exists("RateLimit-Reset"))
                    .andExpect(jsonPath("$.code").value(-1))
                    .andExpect(jsonPath("$.message").isNotEmpty());
        });
    }

    @Test
    void forgedXffIgnoredWhenProxyNotTrusted() {
        runner.withUserConfiguration(InlineIpController.class)
                .run(context -> {
                    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).build();
                    for (int i = 0; i < 3; i++) {
                        mockMvc.perform(get("/inline")
                                        .header("X-Forwarded-For", "8.8.8.8")
                                        .accept(MediaType.APPLICATION_JSON))
                                .andExpect(i < 2 ? status().isOk() : status().isTooManyRequests());
                    }
                });
    }

    @Test
    void startupFailsWhenNamedPolicyMissing() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        JacksonAutoConfiguration.class,
                        PlatformRateLimitCoreAutoConfiguration.class,
                        PlatformRateLimitWebMvcAutoConfiguration.class,
                        WebMvcAutoConfiguration.class,
                        DispatcherServletAutoConfiguration.class))
                .withUserConfiguration(MissingPolicyController.class)
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
    static class InlineIpController {
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

    @Configuration
    static class MissingPolicyController {
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
