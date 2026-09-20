package cn.miniants.platform.starter.webflux;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class WebFluxStarterIsolationTest {

    @Test
    void servletAndMvcRateLimitAreNotOnClasspath() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("org.springframework.web.servlet.DispatcherServlet"));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("cn.miniants.platform.ratelimit.webmvc.RateLimitHandlerInterceptor"));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("cn.miniants.platform.ratelimit.webmvc.RateLimitExceededAdvice"));
    }
}
