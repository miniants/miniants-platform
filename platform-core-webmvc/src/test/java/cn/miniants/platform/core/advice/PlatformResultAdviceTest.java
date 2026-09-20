package cn.miniants.platform.core.advice;

import cn.miniants.platform.core.api.ApiResult;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformResultAdviceTest {

    @Test
    void adviceCoversAllControllersWhenWebEnabled() {
        RestControllerAdvice advice = PlatformResultAdvice.class.getAnnotation(RestControllerAdvice.class);
        assertArrayEquals(new String[0], advice.value());
    }

    @Test
    void alreadyWrappedSkipsOwnEnvelope() {
        assertTrue(PlatformResultAdvice.alreadyWrapped(ApiResult.ok("x")));
    }
}
