package cn.miniants.platform.demo;

import cn.miniants.platform.observability.TraceIds;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PlatformDemoObservabilityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthzEchoesTraceId() throws Exception {
        mockMvc.perform(get("/healthz").header(TraceIds.HEADER_TRACE_ID, "a1b2c3d4e5f60708"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIds.HEADER_TRACE_ID, "a1b2c3d4e5f60708"));
    }

    @Test
    void healthzGeneratesTraceId() throws Exception {
        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIds.HEADER_TRACE_ID, matchesPattern("[0-9a-f]{16}")));
    }

}
