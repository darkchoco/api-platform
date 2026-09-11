package darkchoco.apmapi.config.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// application-dev.yml의 app.cors.allowed-origins(localhost:5173)를 기준으로 검증한다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class CorsConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void allowsConfiguredDevOrigin() throws Exception {
        mockMvc.perform(get("/actuator/health").header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void rejectsUnlistedOrigin() throws Exception {
        mockMvc.perform(get("/actuator/health").header("Origin", "http://evil.example.com"))
                .andExpect(status().isForbidden());
    }
}
