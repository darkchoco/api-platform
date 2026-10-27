package darkchoco.apmapi.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 종목 투자계획금액 저장 API(PORT-BE-4)를 실제 dev DB에 대해 검증한다. 테스트마다 롤백되어 dev 데이터를 남기지 않고,
 * 시계를 2099-01-15 10:00:00(Asia/Seoul)로 고정한다. dev DB(43306)와 DB_PASSWORD가 필요하다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@Import(PortfolioVersionApiTest.FixedClockConfig.class)
class PortfolioItemPlanApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcClient jdbc;

    @Test
    void putKrwPlan_storesOnlyInKrwColumn_overwritesAndRecalculatesRatioFromLatestVersion() throws Exception {
        Map<String, Object> item = firstItemOf("KRW");
        int productId = (Integer) item.get("productId");
        int schemeAcId = (Integer) item.get("schemeAcId");
        // 최근 버전: 대상 100%, 계획금 10,000,000원 → 8,000,000 ÷ 10,000,000 = 0.8
        createVersionWithFullTarget(schemeAcId);

        String response = putPlan(productId, "8000000").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<Integer>read(response, "$.productId")).isEqualTo(productId);
        assertThat(JsonPath.<Integer>read(response, "$.planKrw")).isEqualTo(8_000_000);
        assertThat(JsonPath.<Object>read(response, "$.planUsd")).isNull();
        assertThat(JsonPath.<Object>read(response, "$.planEur")).isNull();
        assertThat(JsonPath.<Number>read(response, "$.targetRatio").doubleValue()).isCloseTo(0.8, within(0.00001));

        PlanRow row = planRow(productId);
        assertThat(row.planKrw()).isEqualByComparingTo("8000000");
        assertThat(row.planUsd()).isNull();
        assertThat(row.planEur()).isNull();
        assertThat(row.updDate()).isEqualTo(LocalDateTime.of(2099, 1, 15, 10, 0, 0));

        // 다시 저장하면 행을 늘리지 않고 덮어쓴다
        putPlan(productId, "5000000").andExpect(status().isOk());
        assertThat(planRowCount(productId)).isEqualTo(1);
        assertThat(planRow(productId).planKrw()).isEqualByComparingTo("5000000");
    }

    @Test
    void putUsdPlan_storesOnlyInUsdColumnAndClearsStaleOtherColumns() throws Exception {
        Map<String, Object> item = firstItemOf("USD");
        int productId = (Integer) item.get("productId");
        // 통화가 바뀐 경우 등으로 다른 통화 컬럼에 값이 남아 있는 상태를 만든다
        jdbc.sql("""
                        INSERT INTO portfolio_item_plan (product_id, plan_krw, plan_usd, plan_eur, upd_date)
                        VALUES (:productId, 999, NULL, 77.00, NOW())
                        ON DUPLICATE KEY UPDATE plan_krw = 999, plan_usd = NULL, plan_eur = 77.00
                        """)
                .param("productId", productId).update();

        String response = putPlan(productId, "1234.56").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<Number>read(response, "$.planUsd").doubleValue()).isCloseTo(1234.56, within(0.001));
        assertThat(JsonPath.<Object>read(response, "$.planKrw")).isNull();
        assertThat(JsonPath.<Object>read(response, "$.planEur")).isNull();

        PlanRow row = planRow(productId);
        assertThat(row.planUsd()).isEqualByComparingTo("1234.56");
        assertThat(row.planKrw()).isNull();
        assertThat(row.planEur()).isNull();
    }

    @Test
    void putEurPlan_storesOnlyInEurColumn() throws Exception {
        int productId = (Integer) firstItemOf("EUR").get("productId");

        putPlan(productId, "300.5").andExpect(status().isOk());

        PlanRow row = planRow(productId);
        assertThat(row.planEur()).isEqualByComparingTo("300.50");
        assertThat(row.planKrw()).isNull();
        assertThat(row.planUsd()).isNull();
    }

    @Test
    void putNullPlan_deletesTheRowAndReturnsItemWithoutPlan() throws Exception {
        int productId = (Integer) firstItemOf("KRW").get("productId");
        putPlan(productId, "8000000").andExpect(status().isOk());
        assertThat(planRowCount(productId)).isEqualTo(1);

        String response = putPlan(productId, "null").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(planRowCount(productId)).isZero();
        assertThat(JsonPath.<Object>read(response, "$.planKrw")).isNull();
        assertThat(JsonPath.<Object>read(response, "$.targetRatio")).isNull();
        // 계획이 없는 종목을 다시 지워도 오류가 아니다
        putPlan(productId, "null").andExpect(status().isOk());
    }

    @Test
    void savedPlanIsVisibleInTheItemListing() throws Exception {
        Map<String, Object> item = firstItemOf("KRW");
        int productId = (Integer) item.get("productId");

        putPlan(productId, "8000000").andExpect(status().isOk());

        Map<String, Object> listed = items("/api/portfolio/items?schemeAcId=" + item.get("schemeAcId")).stream()
                .filter(i -> Integer.valueOf(productId).equals(i.get("productId"))).findFirst().orElseThrow();
        assertThat(((Number) listed.get("planKrw")).longValue()).isEqualTo(8_000_000L);
    }

    @Test
    void putPlan_errors() throws Exception {
        int krwProductId = (Integer) firstItemOf("KRW").get("productId");
        int usdProductId = (Integer) firstItemOf("USD").get("productId");

        putPlan(2_000_000_000, "100").andExpect(status().isNotFound());
        putPlan(krwProductId, "-1").andExpect(status().isBadRequest());
        putPlan(krwProductId, "8000000.5").andExpect(status().isBadRequest());
        putPlan(usdProductId, "1.234").andExpect(status().isBadRequest());
        putPlan(usdProductId, "10000000000.00").andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/portfolio/items/" + krwProductId + "/plan")
                        .contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/portfolio/items/abc/plan")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"planAmount\": 1}"))
                .andExpect(status().isBadRequest());

        // 검증에 실패한 요청은 아무것도 저장하지 않는다
        assertThat(planRowCount(krwProductId)).isZero();
        assertThat(planRowCount(usdProductId)).isZero();
    }

    @Test
    void putPlan_forExistingProductThatIsNotHeld_isBadRequest() throws Exception {
        List<Integer> listedIds = items("/api/portfolio/items").stream().map(i -> (Integer) i.get("productId")).toList();
        Integer notHeld = jdbc.sql("SELECT product_id FROM product").query(Integer.class).list().stream()
                .filter(id -> !listedIds.contains(id)).findFirst().orElse(null);
        assumeThat(notHeld).as("dev DB에 보유 중이 아닌 종목이 있어야 한다").isNotNull();

        putPlan(notHeld, "100").andExpect(status().isBadRequest());
        putPlan(notHeld, "null").andExpect(status().isBadRequest());
        assertThat(planRowCount(notHeld)).isZero();
    }

    private ResultActions putPlan(int productId, String planAmountJson) throws Exception {
        return mockMvc.perform(put("/api/portfolio/items/" + productId + "/plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planAmount\": " + planAmountJson + "}"));
    }

    private void createVersionWithFullTarget(int schemeAcId) throws Exception {
        mockMvc.perform(post("/api/portfolio/versions").contentType(MediaType.APPLICATION_JSON).content("""
                        { "planKrw": 10000000, "fxUsd": 1350.50,
                          "detail": [ { "schemeAcId": %d, "krwUsdRatio": 1, "eurRatio": 0 } ] }
                        """.formatted(schemeAcId)))
                .andExpect(status().isCreated());
    }

    private Map<String, Object> firstItemOf(String currency) throws Exception {
        Map<String, Object> item = items("/api/portfolio/items").stream()
                .filter(i -> currency.equals(i.get("currency"))).findFirst().orElse(null);
        assumeThat(item).as("dev DB에 %s 보유 종목이 있어야 한다", currency).isNotNull();
        return item;
    }

    private List<Map<String, Object>> items(String url) throws Exception {
        String body = mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$");
    }

    private int planRowCount(int productId) {
        return jdbc.sql("SELECT COUNT(*) FROM portfolio_item_plan WHERE product_id = :productId")
                .param("productId", productId).query(Integer.class).single();
    }

    private PlanRow planRow(int productId) {
        return jdbc.sql("SELECT plan_krw, plan_usd, plan_eur, upd_date FROM portfolio_item_plan WHERE product_id = :productId")
                .param("productId", productId)
                .query((rs, n) -> new PlanRow(rs.getBigDecimal("plan_krw"), rs.getBigDecimal("plan_usd"),
                        rs.getBigDecimal("plan_eur"), rs.getObject("upd_date", LocalDateTime.class)))
                .single();
    }

    private record PlanRow(BigDecimal planKrw, BigDecimal planUsd, BigDecimal planEur, LocalDateTime updDate) {
    }
}
