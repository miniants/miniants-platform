package cn.miniants.platform.observability;

import cn.miniants.platform.security.HeaderCurrentUserFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = ObservabilityTestApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.application.name=platform-observability-test",
        "platform.security.header-auth=true"
})
class ObservabilityWebTest {

    @Autowired
    private MockMvc mockMvc;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesAndClearsTraceId() throws Exception {
        MvcResult result = mockMvc.perform(get("/__obs/trace"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIds.HEADER_TRACE_ID, matchesPattern("[0-9a-f]{16}")))
                .andExpect(jsonPath("$.data.traceId", matchesPattern("[0-9a-f]{16}")))
                .andReturn();
        String header = result.getResponse().getHeader(TraceIds.HEADER_TRACE_ID);
        assertTrue(result.getResponse().getContentAsString().contains(header));
        assertNull(MDC.get(TraceIds.MDC_TRACE_ID));
    }

    @Test
    void echoesIncomingTraceId() throws Exception {
        mockMvc.perform(get("/__obs/trace").header(TraceIds.HEADER_TRACE_ID, "a1b2c3d4e5f60708"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIds.HEADER_TRACE_ID, "a1b2c3d4e5f60708"))
                .andExpect(jsonPath("$.data.traceId").value("a1b2c3d4e5f60708"))
                .andExpect(jsonPath("$.data.uid").value("-"));
    }

    @Test
    void writesUidFromCurrentUser() throws Exception {
        mockMvc.perform(get("/__obs/trace")
                        .header(TraceIds.HEADER_TRACE_ID, "a1b2c3d4e5f60708")
                        .header(HeaderCurrentUserFilter.USER, "12:alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uid").value("alice"));
    }

    @Test
    void writesClientIpFromForwardedFor() throws Exception {
        mockMvc.perform(get("/__obs/trace")
                        .header(TraceIds.HEADER_TRACE_ID, "a1b2c3d4e5f60708")
                        .header("X-Forwarded-For", "10.0.0.8, 10.0.0.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ip").value("10.0.0.8"));
        assertNull(MDC.get(TraceIds.MDC_IP));
    }

    @Test
    void asyncTaskSeesSameTraceId() throws Exception {
        mockMvc.perform(get("/__obs/async").header(TraceIds.HEADER_TRACE_ID, "a1b2c3d4e5f60708"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.request").value("a1b2c3d4e5f60708"))
                .andExpect(jsonPath("$.data.async").value("a1b2c3d4e5f60708"));
    }
}
