package cn.miniants.platform.ops;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = OpsTestApplication.class)
@AutoConfigureMockMvc
class OpsWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RuntimeDrain drain;

    @Test
    void stateIsRawAndStartsNotDraining() throws Exception {
        drain.setDraining(false);
        mockMvc.perform(get("/internal/runtime/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.process.draining").value(false))
                .andExpect(jsonPath("$.process.startedAt").exists());
    }

    @Test
    void drainTogglesProcessFlag() throws Exception {
        drain.setDraining(false);
        mockMvc.perform(put("/internal/runtime/drain").param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.process.draining").value(true));
        mockMvc.perform(put("/internal/runtime/drain").param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.process.draining").value(false));
    }

    @Test
    void rejectsNonLoopback() throws Exception {
        mockMvc.perform(get("/internal/runtime/state").with(request -> {
                    request.setRemoteAddr("10.0.0.8");
                    return request;
                }))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("仅本机可访问运行时运维口"));
    }
}
