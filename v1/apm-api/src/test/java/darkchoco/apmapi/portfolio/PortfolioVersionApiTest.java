package darkchoco.apmapi.portfolio;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Portfolio 구성 API(PORT-BE-1)를 실제 dev DB에 대해 끝까지 검증한다.
 * 각 테스트는 트랜잭션이 롤백되어 dev 데이터를 남기지 않고, 시계를 2099-01로 고정해
 * 기존 버전 라벨(v{YYMM}.n)과 충돌하지 않는다. dev DB(43306)와 DB_PASSWORD가 필요하다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@Import(PortfolioVersionApiTest.FixedClockConfig.class)
class PortfolioVersionApiTest {

    // 이번 달(2099-01) 라벨은 v9901.0
    private static final String CREATE_BODY = """
            {
              "availKrw": 50000000, "availUsd": 20000.00, "availEur": 5000.00,
              "planKrw": 45000000, "planUsd": 18000.00, "planEur": 4500.00,
              "fxUsd": 1350.50, "fxEur": 1470.20, "fetchDate": "2099-01-15T09:12:00",
              "detail": [
                { "schemeAcId": 1, "krwUsdRatio": 0.60, "eurRatio": 1, "execKrw": 15000000, "execUsd": 0, "execEur": 0 },
                { "schemeAcId": 5, "krwUsdRatio": 0.40, "eurRatio": 0 }
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getTargets_returnsPortfolioTargetsInSchemeAcIdOrder() throws Exception {
        mockMvc.perform(get("/api/portfolio/targets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].schemeAcId", is(1)))
                .andExpect(jsonPath("$[0].name", is("국내주식/ETF")))
                .andExpect(jsonPath("$[4].schemeAcId", is(5)));
    }

    @Test
    void createVersion_withoutBase_createsThisMonthZeroAndReadsBackAllTargets() throws Exception {
        mockMvc.perform(post("/api/portfolio/versions").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionLabel", is("v9901.0")))
                .andExpect(header().string("Location", startsWith("/api/portfolio/versions/")));

        mockMvc.perform(get("/api/portfolio/versions/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionLabel", is("v9901.0")))
                .andExpect(jsonPath("$.availKrw", is(50000000)))
                .andExpect(jsonPath("$.fxUsd", is(1350.50)))
                .andExpect(jsonPath("$.fetchDate", is("2099-01-15T09:12:00")))
                .andExpect(jsonPath("$.insDate", is("2099-01-15T10:00:00")))
                // 저장한 건 2건뿐이지만 항상 대상 전부(현재 7건)를 schemeAcId 순으로 내려준다
                .andExpect(jsonPath("$.detail", hasSize(7)))
                .andExpect(jsonPath("$.detail[0].schemeAcId", is(1)))
                .andExpect(jsonPath("$.detail[0].krwUsdRatio", is(0.6)))
                .andExpect(jsonPath("$.detail[0].execKrw", is(15000000)))
                .andExpect(jsonPath("$.detail[1].schemeAcId", is(2)))
                .andExpect(jsonPath("$.detail[1].krwUsdRatio", is(0)))
                .andExpect(jsonPath("$.detail[1].execKrw", is(nullValue())))
                .andExpect(jsonPath("$.detail[4].schemeAcId", is(5)))
                .andExpect(jsonPath("$.detail[4].krwUsdRatio", is(0.4)));
    }

    @Test
    void createVersion_withoutBase_conflictsWhenThisMonthZeroAlreadyExists() throws Exception {
        create(CREATE_BODY).andExpect(status().isCreated());

        create(CREATE_BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", containsString("v9901.0")));
    }

    @Test
    void createVersion_withBase_bumpsSerialAndNextLabelsPreviewsIt() throws Exception {
        int baseId = createdVersionId(CREATE_BODY);

        mockMvc.perform(get("/api/portfolio/versions/next-labels").param("baseVersionId", String.valueOf(baseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thisMonth.versionLabel", is("v9901.0")))
                .andExpect(jsonPath("$.thisMonth.available", is(false)))
                .andExpect(jsonPath("$.fromBase.versionLabel", is("v9901.1")))
                .andExpect(jsonPath("$.fromBase.baseVersionLabel", is("v9901.0")));

        create(CREATE_BODY.replaceFirst("\\{", "{ \"baseVersionId\": " + baseId + ","))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionLabel", is("v9901.1")));
    }

    @Test
    void nextLabels_withoutBase_hasNullFromBase() throws Exception {
        mockMvc.perform(get("/api/portfolio/versions/next-labels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thisMonth.available", is(true)))
                .andExpect(jsonPath("$.fromBase", is(nullValue())));
    }

    @Test
    void nextLabels_withUnknownBase_isNotFound() throws Exception {
        mockMvc.perform(get("/api/portfolio/versions/next-labels").param("baseVersionId", "2000000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateVersion_keepsLabelAndReplacesDetailWholesale() throws Exception {
        int versionId = createdVersionId(CREATE_BODY);

        String updateBody = """
                {
                  "planKrw": 1000000, "planUsd": null, "planEur": null,
                  "detail": [ { "schemeAcId": 2, "krwUsdRatio": 1, "eurRatio": 0 } ]
                }
                """;
        mockMvc.perform(put("/api/portfolio/versions/" + versionId)
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionLabel", is("v9901.0")))
                .andExpect(jsonPath("$.planKrw", is(1000000)))
                .andExpect(jsonPath("$.fxUsd", is(nullValue())))
                // 이전 detail(1번 대상 0.6)은 통째로 교체되어 사라진다
                .andExpect(jsonPath("$.detail[0].krwUsdRatio", is(0)))
                .andExpect(jsonPath("$.detail[1].schemeAcId", is(2)))
                .andExpect(jsonPath("$.detail[1].krwUsdRatio", is(1.0)));

        mockMvc.perform(get("/api/portfolio/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].versionId", is(versionId)))
                .andExpect(jsonPath("$[0].updatedAt", is("2099-01-15T10:00:00")));
    }

    @Test
    void updateVersion_ofMissingVersion_isNotFound() throws Exception {
        mockMvc.perform(put("/api/portfolio/versions/2000000000")
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void getVersion_ofMissingVersion_isNotFound() throws Exception {
        mockMvc.perform(get("/api/portfolio/versions/2000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.path", is("/api/portfolio/versions/2000000000")));
    }

    @Test
    void requestErrors_areBadRequest() throws Exception {
        String fractionalPercent = CREATE_BODY.replace("0.60", "0.605").replace("0.40", "0.395");
        String unknownTarget = CREATE_BODY.replace("\"schemeAcId\": 5", "\"schemeAcId\": 99");

        create(fractionalPercent).andExpect(status().isBadRequest());
        create(unknownTarget).andExpect(status().isBadRequest());
        create("not json").andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/portfolio/versions/abc")).andExpect(status().isBadRequest());
    }

    private ResultActions create(String body) throws Exception {
        return mockMvc.perform(post("/api/portfolio/versions").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private int createdVersionId(String body) throws Exception {
        String json = create(body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.versionId");
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            // 2099-01-15 10:00:00 Asia/Seoul
            return Clock.fixed(Instant.parse("2099-01-15T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }
}
