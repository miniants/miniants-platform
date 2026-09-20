package cn.miniants.platform.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PlatformDemoApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthzWrapsAsApiResultWithNumericCode() throws Exception {
        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("ok"));
    }

    @Test
    void unknownExceptionDoesNotLeakInternalMessage() throws Exception {
        mockMvc.perform(get("/__demo/boom"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message", startsWith("服务暂不可用，请稍后重试")))
                .andExpect(jsonPath("$.message", not(containsString("secret_table"))))
                .andExpect(jsonPath("$.message", not(containsString("SQL"))));
    }

    /** 部署机器的 LANG 不该决定中文用户看到什么语言，所以不带头部时必须是中文。 */
    @Test
    void errorTextDefaultsToChineseWhenTheClientAsksForNothing() throws Exception {
        mockMvc.perform(get("/__demo/boom"))
                .andExpect(jsonPath("$.message", startsWith("服务暂不可用")));
    }

    @Test
    void errorTextFollowsAcceptLanguage() throws Exception {
        mockMvc.perform(get("/__demo/boom").header("Accept-Language", "en-US"))
                .andExpect(jsonPath("$.message", startsWith("Service temporarily unavailable")));
    }
}
