package cn.miniants.platform.observability;

import cn.miniants.platform.security.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CurrentUserTraceUidSupplierTest {

    private final CurrentUserTraceUidSupplier supplier = new CurrentUserTraceUidSupplier();

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void currentRequestReadsBoundUserWithoutRequestContextHolder() {
        RequestContextHolder.resetRequestAttributes();
        MockHttpServletRequest request = new MockHttpServletRequest();
        CurrentUser.bind(request, CurrentUser.user(12L, "alice").build());

        assertEquals(TraceIds.ANON_UID, supplier.current());
        assertEquals("alice", supplier.current(request));
    }

    @Test
    void currentRequestFallsBackToClientId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        CurrentUser.bind(request, CurrentUser.client("JWY_STU_KIOSK").build());

        assertEquals("JWY_STU_KIOSK", supplier.current(request));
    }
}
