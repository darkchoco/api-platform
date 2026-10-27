package darkchoco.apmapi.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * '값 가져오기' API(PORT-BE-2)를 실제 dev DB에 대해 검증한다 (읽기 전용, 데이터를 쓰지 않는다).
 * 금액 자체는 importer가 적재하는 값이라 고정 숫자를 단정하지 않고 형태와 규칙만 확인한다.
 * dev DB(43306)와 DB_PASSWORD가 필요하다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PortfolioExecutionSnapshotApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void manualFx_returnsAllTargetsInOrderWithoutLookingUpExRate() throws Exception {
        mockMvc.perform(get("/api/portfolio/execution-snapshot").param("fxUsd", "1350.50").param("fxEur", "1470.20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseDate", notNullValue()))
                .andExpect(jsonPath("$.fxUsd", is(1350.50)))
                .andExpect(jsonPath("$.fxEur", is(1470.20)))
                .andExpect(jsonPath("$.fxSource", is("MANUAL")))
                .andExpect(jsonPath("$.fxDate", is(nullValue())))
                .andExpect(jsonPath("$.fetchDate", notNullValue()))
                .andExpect(jsonPath("$.exec", hasSize(7)))
                .andExpect(jsonPath("$.exec[0].schemeAcId", is(1)))
                .andExpect(jsonPath("$.exec[6].schemeAcId", is(7)));
    }

    @Test
    void withoutFx_usesExRateWithinThreeDaysOrReportsMissing() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/portfolio/execution-snapshot"))
                .andReturn().getResponse();

        // dev 데이터에 따라 환율이 있으면 200(EX_RATE), 없으면 422 - 둘 외의 상태는 있을 수 없다.
        if (response.getStatus() == 200) {
            String body = response.getContentAsString();
            assertThat(JsonPath.<String>read(body, "$.fxSource")).isEqualTo("EX_RATE");
            assertThat(JsonPath.<String>read(body, "$.fxDate")).isNotNull();
            assertThat(JsonPath.<List<Object>>read(body, "$.exec")).hasSize(7);
        } else {
            assertThat(response.getStatus()).isEqualTo(422);
        }
    }

    @Test
    void onlyOneFx_isBadRequest() throws Exception {
        mockMvc.perform(get("/api/portfolio/execution-snapshot").param("fxUsd", "1350.50"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/portfolio/execution-snapshot").param("fxEur", "1470.20"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedFx_isBadRequest() throws Exception {
        mockMvc.perform(get("/api/portfolio/execution-snapshot").param("fxUsd", "abc").param("fxEur", "1470.20"))
                .andExpect(status().isBadRequest());
    }
}
