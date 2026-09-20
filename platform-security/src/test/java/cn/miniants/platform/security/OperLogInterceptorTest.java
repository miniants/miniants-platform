package cn.miniants.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OperLogInterceptorTest {

    @Test
    void recordsAnnotatedMethodWithCurrentUser() throws Exception {
        List<OperLogEntry> recorded = new ArrayList<>();
        OperLogInterceptor interceptor = new OperLogInterceptor(recorded::add);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/platform/admin/user/save");
        request.setQueryString("dryRun=1");
        CurrentUser.bind(request, CurrentUser.user(0L, "root").name("系统").build());
        HandlerMethod handler = handler("saveUser");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, handler));
        interceptor.afterCompletion(request, response, handler, null);

        assertEquals(1, recorded.size());
        OperLogEntry entry = recorded.get(0);
        assertEquals("保存用户", entry.title());
        assertEquals(OperLogEventTypes.RBAC_CHANGE, entry.eventType());
        assertEquals("POST", entry.httpMethod());
        assertEquals("/platform/admin/user/save", entry.requestUri());
        assertEquals("POST /platform/admin/user/save?dryRun=1", entry.requestParam());
        assertTrue(entry.success());
        assertEquals(0L, entry.operatorId());
        assertEquals("系统", entry.operatorName());
        assertNull(entry.errorMessage());
    }

    @Test
    void skipsUnmarkedMethod() throws Exception {
        List<OperLogEntry> recorded = new ArrayList<>();
        OperLogInterceptor interceptor = new OperLogInterceptor(recorded::add);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/platform/admin/user/page");
        interceptor.afterCompletion(request, new MockHttpServletResponse(), handler("page"), null);
        assertTrue(recorded.isEmpty());
    }

    @Test
    void recordsFailureAndSwallowsRecorderError() throws Exception {
        OperLogInterceptor interceptor = new OperLogInterceptor(entry -> {
            throw new IllegalStateException("db down");
        });
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/platform/admin/user/1");
        HandlerMethod handler = handler("deleteUser");
        interceptor.preHandle(request, new MockHttpServletResponse(), handler);
        interceptor.afterCompletion(
                request, new MockHttpServletResponse(), handler, new IllegalStateException("业务失败"));
    }

    @Test
    void failedRequestStoresErrorMessage() throws Exception {
        List<OperLogEntry> recorded = new ArrayList<>();
        OperLogInterceptor interceptor = new OperLogInterceptor(recorded::add);
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/platform/admin/user/1");
        HandlerMethod handler = handler("deleteUser");
        interceptor.afterCompletion(
                request, new MockHttpServletResponse(), handler, new IllegalStateException("业务失败"));
        assertEquals(false, recorded.get(0).success());
        assertEquals("业务失败", recorded.get(0).errorMessage());
        assertEquals(OperLogEventTypes.RBAC_CHANGE, recorded.get(0).eventType());
        assertNull(recorded.get(0).requestParam());
    }

    private static HandlerMethod handler(String method) throws Exception {
        return new HandlerMethod(new Fixture(), Fixture.class.getMethod(method));
    }

    static class Fixture {
        @OperLog(value = "保存用户", eventType = OperLogEventTypes.RBAC_CHANGE, captureRequest = true)
        public void saveUser() {
        }

        @OperLog(value = "删除用户", eventType = OperLogEventTypes.RBAC_CHANGE)
        public void deleteUser() {
        }

        public void page() {
        }
    }
}
