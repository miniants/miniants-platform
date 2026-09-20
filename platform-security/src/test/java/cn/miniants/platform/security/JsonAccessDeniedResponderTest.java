package cn.miniants.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonAccessDeniedResponderTest {

    @Test
    void writesSpringErrorJsonAndStopsMvc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/page");
        MockHttpServletResponse response = new MockHttpServletResponse();
        JsonAccessDeniedResponder responder = new JsonAccessDeniedResponder();

        assertFalse(responder.deny(request, response, HttpStatus.FORBIDDEN, "权限不足"));
        assertEquals(403, response.getStatus());
        String body = response.getContentAsString();
        assertTrue(body.contains("\"status\":403"));
        assertTrue(body.contains("\"message\":\"权限不足\""));
        assertTrue(body.contains("\"path\":\"/admin/page\""));
    }
}
