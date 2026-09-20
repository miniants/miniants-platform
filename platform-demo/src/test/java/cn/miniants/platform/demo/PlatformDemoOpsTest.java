package cn.miniants.platform.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PlatformDemoOpsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void runtimeStateIsRawMap() throws Exception {
        mockMvc.perform(get("/internal/runtime/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.process.draining").exists());
    }
}
