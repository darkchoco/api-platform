package darkchoco.apmapi.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 종목별 세부 현황 API(PORT-BE-3)를 실제 dev DB에 대해 검증한다. 테스트마다 롤백되므로 dev 데이터를 남기지 않는다.
 * 보유 금액은 importer가 적재하는 값이라 고정 숫자를 단정하지 않고, 값 가져오기 API(PORT-BE-2)와의 정합성과
 * 계산 규칙만 확인한다. dev DB(43306)와 DB_PASSWORD가 필요하다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@Import(PortfolioVersionApiTest.FixedClockConfig.class)
class PortfolioItemApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcClient jdbc;

    @Test
    void items_areOrderedBySchemeAcIdAndOnlyContainPortfolioCurrencies() throws Exception {
        List<Map<String, Object>> items = getItems("/api/portfolio/items");

        assertThat(items).isNotEmpty();
        assertThat(items).extracting(item -> (Integer) item.get("schemeAcId")).isSorted();
        assertThat(items).extracting(item -> (String) item.get("currency")).isSubsetOf("KRW", "USD", "EUR");
        // 같은 종목이 두 번 나오면 안 된다 (여러 계좌 보유분은 합산)
        assertThat(items).extracting(item -> (Integer) item.get("productId")).doesNotHaveDuplicates();
    }

    @Test
    void currentAmountsPerTarget_matchTheExecutionSnapshot() throws Exception {
        List<Map<String, Object>> items = getItems("/api/portfolio/items");
        String snapshot = mockMvc.perform(get("/api/portfolio/execution-snapshot")
                        .param("fxUsd", "1").param("fxEur", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> exec = JsonPath.read(snapshot, "$.exec");

        for (Map<String, Object> target : exec) {
            Integer schemeAcId = (Integer) target.get("schemeAcId");
            List<Map<String, Object>> targetItems = items.stream()
                    .filter(item -> schemeAcId.equals(item.get("schemeAcId"))).toList();

            assertThat(sum(targetItems, "currentKrw")).as("target %d KRW", schemeAcId)
                    .isCloseTo(((Number) target.get("execKrw")).doubleValue(), within(0.5));
            assertThat(sum(targetItems, "currentUsd")).as("target %d USD", schemeAcId)
                    .isCloseTo(((Number) target.get("execUsd")).doubleValue(), within(0.005));
            assertThat(sum(targetItems, "currentEur")).as("target %d EUR", schemeAcId)
                    .isCloseTo(((Number) target.get("execEur")).doubleValue(), within(0.005));
        }
    }

    @Test
    void schemeAcIdFilter_returnsOnlyThatTarget() throws Exception {
        List<Map<String, Object>> all = getItems("/api/portfolio/items");
        assumeThat(all).isNotEmpty();
        Integer schemeAcId = (Integer) all.getFirst().get("schemeAcId");

        List<Map<String, Object>> filtered = getItems("/api/portfolio/items?schemeAcId=" + schemeAcId);

        assertThat(filtered).isNotEmpty();
        assertThat(filtered).allSatisfy(item -> assertThat(item.get("schemeAcId")).isEqualTo(schemeAcId));
        assertThat(filtered).hasSize((int) all.stream().filter(item -> schemeAcId.equals(item.get("schemeAcId"))).count());
    }

    @Test
    void unknownTarget_returnsEmptyListAndMalformedTarget_isBadRequest() throws Exception {
        mockMvc.perform(get("/api/portfolio/items").param("schemeAcId", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/portfolio/items").param("schemeAcId", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void itemPlanAndLatestVersion_produceTargetRatioFromTheDatabase() throws Exception {
        List<Map<String, Object>> items = getItems("/api/portfolio/items");
        Map<String, Object> krwItem = items.stream()
                .filter(item -> "KRW".equals(item.get("currency"))).findFirst().orElse(null);
        assumeThat(krwItem).as("dev DB에 원화 보유 종목이 있어야 한다").isNotNull();
        int productId = (Integer) krwItem.get("productId");
        int schemeAcId = (Integer) krwItem.get("schemeAcId");

        // 종목 계획 8,000,000원 + 최근 버전(대상 100%, 계획금 10,000,000원) → 8,000,000 ÷ 10,000,000 = 0.8
        jdbc.sql("""
                        INSERT INTO portfolio_item_plan (product_id, plan_krw, plan_usd, plan_eur, upd_date)
                        VALUES (:productId, 8000000, NULL, NULL, NOW())
                        ON DUPLICATE KEY UPDATE plan_krw = 8000000, plan_usd = NULL, plan_eur = NULL
                        """)
                .param("productId", productId).update();
        mockMvc.perform(post("/api/portfolio/versions").contentType(MediaType.APPLICATION_JSON).content("""
                        { "planKrw": 10000000, "fxUsd": 1350.50,
                          "detail": [ { "schemeAcId": %d, "krwUsdRatio": 1, "eurRatio": 0 } ] }
                        """.formatted(schemeAcId)))
                .andExpect(status().isCreated());

        Map<String, Object> updated = getItems("/api/portfolio/items?schemeAcId=" + schemeAcId).stream()
                .filter(item -> Integer.valueOf(productId).equals(item.get("productId"))).findFirst().orElseThrow();

        assertThat(((Number) updated.get("planKrw")).longValue()).isEqualTo(8_000_000L);
        assertThat(updated.get("planUsd")).isNull();
        assertThat(updated.get("planEur")).isNull();
        assertThat(((Number) updated.get("targetRatio")).doubleValue()).isCloseTo(0.8, within(0.00001));
    }

    private List<Map<String, Object>> getItems(String url) throws Exception {
        String body = mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$");
    }

    private double sum(List<Map<String, Object>> items, String field) {
        return items.stream().mapToDouble(item -> ((Number) item.get(field)).doubleValue()).sum();
    }
}
