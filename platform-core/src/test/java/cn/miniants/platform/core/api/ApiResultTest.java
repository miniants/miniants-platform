package cn.miniants.platform.core.api;

import cn.miniants.platform.core.error.PlatformException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiResultTest {

    @Test
    void successCodeStaysNumber200() {
        ApiResult<String> result = ApiResult.ok("ok");
        assertEquals(200L, result.getCode());
        assertTrue(result.ok());
        assertEquals("ok", result.getData());
    }

    @Test
    void failedCodeStaysMinusOne() {
        ApiResult<Void> result = ApiResult.failed("参数错误");
        assertEquals(-1L, result.getCode());
        assertEquals("参数错误", result.getMessage());
        assertThrows(PlatformException.class, result::requireData);
    }

    @Test
    void requireDataReturnsPayloadWhenOk() {
        assertEquals("ok", ApiResult.ok("ok").requireData());
    }
}
