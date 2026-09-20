package cn.miniants.platform.observability;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessLogTest {

    @Test
    void buildsStableKeyValues() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/healthz");
        request.setRemoteAddr("10.0.0.8");
        assertEquals(
                "status=200 ms=12 method=GET path=/healthz auth=none reason=no-guard op=-",
                AccessLog.build(request, 200, 12, null));
        assertEquals(
                "status=500 ms=3 method=GET path=/healthz auth=none reason=no-guard op=- err=IllegalStateException",
                AccessLog.build(request, 500, 3, new IllegalStateException("hidden")));
    }

    @Test
    void usesStampedErrWhenAdviceAlreadyHandled() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/jwy-shared-iot/iot/iot-config/apply/tch");
        request.setRemoteAddr("115.25.40.129");
        request.setAttribute(AccessLog.ATTR_ERR, "NoResourceFoundException");
        assertEquals(
                "status=404 ms=2 method=GET path=/jwy-shared-iot/iot/iot-config/apply/tch auth=none reason=no-guard op=- err=NoResourceFoundException",
                AccessLog.build(request, 404, 2, null));
    }

    @Test
    void includesAuthFieldsWhenStamped() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sys/user/page");
        request.setRemoteAddr("10.0.0.8");
        request.setAttribute(AccessLog.ATTR_AUTH, "ok");
        request.setAttribute(AccessLog.ATTR_REASON, "permission-ok:sys:user:page");
        request.setAttribute(AccessLog.ATTR_ACTOR, "user");
        request.setAttribute(AccessLog.ATTR_OP, "分页列表");
        String msg = AccessLog.build(request, 200, 28, null);
        assertTrue(msg.startsWith("status=200 ms=28 method=GET path=/sys/user/page"));
        assertTrue(msg.contains("auth=ok"));
        assertTrue(msg.contains("reason=permission-ok:sys:user:page"));
        assertTrue(msg.contains("actor=user"));
        assertTrue(msg.contains("op=分页列表"));
    }

    @Test
    void levelsFollowHttpFamilyAndLoginFail() {
        assertEquals(Level.INFO, AccessLog.levelFor(200, null));
        assertEquals(Level.INFO, AccessLog.levelFor(204, null));
        assertEquals(Level.INFO, AccessLog.levelFor(301, "ok"));
        assertEquals(Level.INFO, AccessLog.levelFor(200, "unbound"));
        assertEquals(Level.INFO, AccessLog.levelFor(200, "ok"));
        assertEquals(Level.WARN, AccessLog.levelFor(200, "fail"));
        assertEquals(Level.WARN, AccessLog.levelFor(400, null));
        assertEquals(Level.WARN, AccessLog.levelFor(401, "ok"));
        assertEquals(Level.WARN, AccessLog.levelFor(404, null));
        assertEquals(Level.ERROR, AccessLog.levelFor(500, null));
        assertEquals(Level.ERROR, AccessLog.levelFor(503, "fail"));
    }

    @Test
    void includesLoginOutcome() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/open/miniapp/external");
        request.setRemoteAddr("43.139.209.119");
        request.setAttribute(AccessLog.ATTR_AUTH, "permit");
        request.setAttribute(AccessLog.ATTR_REASON, "public-ok:anon");
        request.setAttribute(AccessLog.ATTR_ACTOR, "anon");
        request.setAttribute(AccessLog.ATTR_OP, "小程序换码登录");
        request.setAttribute(AccessLog.ATTR_LOGIN, "fail");
        String msg = AccessLog.build(request, 200, 126, null);
        assertTrue(msg.contains("auth=permit"));
        assertTrue(msg.contains("actor=anon"));
        assertTrue(msg.contains("login=fail"));
    }

    @Test
    void skipsActuatorFaviconAndUpdaterMetadata() {
        assertTrue(AccessLog.shouldSkip(new MockHttpServletRequest("GET", "/actuator/prometheus")));
        assertTrue(AccessLog.shouldSkip(new MockHttpServletRequest("GET", "/favicon.ico")));
        assertTrue(AccessLog.shouldSkip(new MockHttpServletRequest("GET", "/internal/runtime/state")));
        assertTrue(AccessLog.shouldSkip(new MockHttpServletRequest("PUT", "/internal/runtime")));
        assertTrue(AccessLog.isNoisePath("/x/release/latest.yml"));
        assertTrue(AccessLog.isNoisePath("/x/updater/latest.yaml"));
        assertTrue(AccessLog.isNoisePath("/x/release/app.exe.blockmap"));
        assertFalse(AccessLog.isNoisePath("/x/release/app-1.2.3.zip"));
        assertFalse(AccessLog.isNoisePath("/api/other/latest.yml"));
    }
}
