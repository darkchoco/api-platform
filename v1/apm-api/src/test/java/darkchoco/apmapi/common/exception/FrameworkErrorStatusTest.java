package darkchoco.apmapi.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// 실제 Spring MVC가 던지는 예외가 500으로 바뀌지 않고 제 상태 코드로 나가는지 확인한다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class FrameworkErrorStatusTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unknownPath_returns404() throws Exception {
        mockMvc.perform(get("/api/portfolio/nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/portfolio/nonexistent"));
    }

    @Test
    void wrongMethod_returns405WithAllowHeader() throws Exception {
        mockMvc.perform(post("/api/portfolio/targets"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"))
                .andExpect(jsonPath("$.status").value(405));
    }
}
