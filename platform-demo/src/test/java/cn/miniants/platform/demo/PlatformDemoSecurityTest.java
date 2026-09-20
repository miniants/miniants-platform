package cn.miniants.platform.demo;

import cn.miniants.platform.security.HeaderCurrentUserFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PlatformDemoSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicHealthzStillWorks() throws Exception {
        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shadowRejectsAnonymousOnSecure() throws Exception {
        mockMvc.perform(get("/__demo/secure"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value("未登录"));
    }

    @Test
    void headerUserWithCodeIsOk() throws Exception {
        mockMvc.perform(get("/__demo/secure")
                        .header(HeaderCurrentUserFilter.USER, "1:alice")
                        .header(HeaderCurrentUserFilter.PERMS, "demo:ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("pong"));
    }

    @SpringBootTest
    @AutoConfigureMockMvc
    @TestPropertySource(properties = "platform.security.enforcement=enforce")
    static class Enforce {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void enforceRejectsAnonymous() throws Exception {
            mockMvc.perform(get("/__demo/secure"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(-1))
                    .andExpect(jsonPath("$.message").value("未登录"));
        }

        @Test
        void enforceRejectsMissingCode() throws Exception {
            mockMvc.perform(get("/__demo/secure")
                            .header(HeaderCurrentUserFilter.USER, "1:alice")
                            .header(HeaderCurrentUserFilter.PERMS, "other"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("权限不足"));
        }
    }
}
