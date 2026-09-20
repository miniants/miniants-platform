package cn.miniants.platform.starter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StarterWebStackIsolationTest {

    @Test
    void mvcStarterDoesNotBringWebFlux() {
        assertThat(onClasspath("org.springframework.web.reactive.DispatcherHandler")).isFalse();
        assertThat(onClasspath("cn.miniants.platform.core.PlatformCoreWebFluxAutoConfiguration")).isFalse();
        assertThat(onClasspath("cn.miniants.platform.ratelimit.webflux.config.PlatformRateLimitWebFluxAutoConfiguration"))
                .isFalse();
    }

    @Test
    void mvcStarterDoesNotBringRateLimitAdmin() {
        assertThat(onClasspath("cn.miniants.platform.ratelimit.admin.PlatformRateLimitAdminAutoConfiguration"))
                .isFalse();
    }

    @Test
    void mvcStarterBringsMvcRateLimit() {
        assertThat(onClasspath("cn.miniants.platform.ratelimit.webmvc.config.PlatformRateLimitWebMvcAutoConfiguration"))
                .isTrue();
        assertThat(onClasspath("cn.miniants.platform.integration.ratelimit.RateLimiter")).isFalse();
    }

    private static boolean onClasspath(String className) {
        try {
            Class.forName(className, false, StarterWebStackIsolationTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }
}
