package cn.miniants.platform.security;

import cn.miniants.platform.data.audit.Auditor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentUserAuditorSupplierTest {

    private final CurrentUserAuditorSupplier supplier = new CurrentUserAuditorSupplier();

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void emptyWhenAnonymous() {
        assertTrue(supplier.current().isEmpty());
    }

    @Test
    void usesBoundUserIncludingZeroId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        CurrentUser.bind(request, CurrentUser.user(0L, "root").name("系统管理员").build());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        Auditor auditor = supplier.current().orElseThrow();
        assertEquals(0L, auditor.id());
        assertEquals("root", auditor.name());
    }

    @Test
    void prefersUsernameOverDisplayName() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        CurrentUser.bind(request, CurrentUser.user(1L, "s2023001").name("张三").build());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertEquals("s2023001", supplier.current().orElseThrow().name());
    }
}
