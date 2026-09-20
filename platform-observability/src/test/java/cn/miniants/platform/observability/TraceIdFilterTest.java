package cn.miniants.platform.observability;

import cn.miniants.platform.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TraceIdFilterTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void clearsTraceContextWhenAccessLogIsDisabled() throws Exception {
        TraceIdFilter filter = new TraceIdFilter(false, () -> "alice");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        request.addHeader(TraceIds.HEADER_TRACE_ID, "a1b2c3d4e5f60708");
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.setRemoteAddr("10.0.0.8");
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            assertEquals("a1b2c3d4e5f60708", TraceIds.current().orElseThrow());
            assertEquals("alice", MDC.get(TraceIds.MDC_UID));
            assertEquals("10.0.0.8", MDC.get(TraceIds.MDC_IP));
        });

        assertEquals("a1b2c3d4e5f60708", response.getHeader(TraceIds.HEADER_TRACE_ID));
        assertNull(MDC.get(TraceIds.MDC_TRACE_ID));
        assertNull(MDC.get(TraceIds.MDC_UID));
        assertNull(MDC.get(TraceIds.MDC_IP));
    }

    @Test
    void accessLogRebindsUidFromRequestAttributeWithoutRequestContext() throws Exception {
        List<String> seen = new ArrayList<>();
        TraceUidSupplier supplier = new CurrentUserTraceUidSupplier() {
            @Override
            public String current(HttpServletRequest request) {
                String uid = super.current(request);
                seen.add(uid);
                return uid;
            }
        };
        TraceIdFilter filter = new TraceIdFilter(true, supplier);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            CurrentUser.bind((HttpServletRequest) req, CurrentUser.user(12L, "alice").build());
        });

        assertEquals(List.of("-", "alice"), seen);
    }
}
