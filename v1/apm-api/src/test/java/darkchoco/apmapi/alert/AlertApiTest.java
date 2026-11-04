package darkchoco.apmapi.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import darkchoco.apmapi.alert.repository.MaturityTarget;
import darkchoco.apmapi.alert.repository.MaturityTargetRepository;
import darkchoco.apmapi.alert.service.MaturityRefreshTracker;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 API(ALERT-BE-3)를 실제 dev DB에 대해 검증한다. 테스트마다 롤백되어 dev 데이터를 남기지 않는다.
 * 테스트 시작 시 alert_item을 비우고(롤백됨) 필요한 행을 직접 넣는다. dev DB(43306)와 DB_PASSWORD가 필요하다.
 *
 * <p>"오늘"을 1900-01-01로 두면 모든 투자건의 만기가 30일보다 멀어 만기 재계산이 알림을 만들지 않으므로,
 * 직접 넣은 행만으로 목록·닫기를 확인할 수 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@Import(MaturityAlertRefreshTest.MutableClockConfig.class)
class AlertApiTest {

    private static final LocalDate QUIET_DAY = LocalDate.of(1900, 1, 1);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private MaturityAlertRefreshTest.MutableClock clock;
    @Autowired
    private MaturityRefreshTracker maturityRefreshTracker;
    @Autowired
    private MaturityTargetRepository maturityTargetRepository;

    @BeforeEach
    void clearAlerts() {
        jdbc.sql("DELETE FROM alert_item").update();
    }

    @Test
    void getAlerts_returnsOnlyVisibleSortedBySeverityThenDueDateThenId() throws Exception {
        startQuietDay();
        int mild = insertAlert("MATURITY", "t-mild", "MILD", "OPEN", "N", LocalDate.of(1900, 1, 25));
        int overdueLater = insertAlert("MATURITY", "t-overdue-2", "OVERDUE", "OPEN", "N", LocalDate.of(1899, 12, 1));
        int overdueEarlier = insertAlert("MATURITY", "t-overdue-1", "OVERDUE", "OPEN", "N", LocalDate.of(1899, 11, 1));
        int jiraNoDue = insertAlert("JIRA", "APM-1", "DEFAULT", "OPEN", "N", null);
        int todoist = insertAlert("TODOIST", "123", "DEFAULT", "OPEN", "N", LocalDate.of(1900, 1, 3));
        int urgent = insertAlert("MATURITY", "t-urgent", "URGENT", "OPEN", "N", LocalDate.of(1900, 1, 10));
        // 같은 심각도·같은 날짜면 alertId 순
        int overdueSameDay = insertAlert("MATURITY", "t-overdue-3", "OVERDUE", "OPEN", "N", LocalDate.of(1899, 12, 1));
        // 표시 대상이 아닌 행: 사용자가 닫은 것, 조건에서 벗어난 것
        insertAlert("MATURITY", "t-dismissed", "OVERDUE", "OPEN", "Y", LocalDate.of(1899, 1, 1));
        insertAlert("MATURITY", "t-closed", "OVERDUE", "CLOSE", "N", LocalDate.of(1899, 1, 1));

        String body = mockMvc.perform(get("/api/alerts")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<Integer>>read(body, "$[*].alertId"))
                .containsExactly(overdueEarlier, overdueLater, overdueSameDay, urgent, mild, todoist, jiraNoDue);

        Map<String, Object> first = JsonPath.read(body, "$[0]");
        assertThat(first).containsEntry("sourceType", "MATURITY")
                .containsEntry("tier", "OVERDUE")
                .containsEntry("title", "title t-overdue-1")
                .containsEntry("dueDate", "1899-11-01")
                .containsEntry("daysLeft", -61);
        assertThat(first.get("detailUrl")).isNull();

        Map<String, Object> jira = JsonPath.read(body, "$[6]");
        assertThat(jira).containsEntry("sourceType", "JIRA")
                .containsEntry("tier", "DEFAULT")
                .containsEntry("detailUrl", "https://example.test/browse/APM-1")
                .containsKey("dueDate")
                .containsKey("daysLeft");
        assertThat(jira.get("dueDate")).isNull();
        assertThat(jira.get("daysLeft")).isNull();
    }

    @Test
    void getAlerts_recalculatesMaturityOnlyOnTheFirstCallOfEachDay() throws Exception {
        MaturityTarget target = anyMaturityTarget();
        LocalDate dueDate = target.lastDueDate().toLocalDate();
        String sourceKey = String.valueOf(target.investId());

        // 계산 날짜는 테스트 사이에 남으므로 다른 테스트가 쓰지 않는 날짜를 고른다(refresh 테스트는 -20일)
        clock.setToday(dueDate.minusDays(21));
        mockMvc.perform(get("/api/alerts")).andExpect(status().isOk());
        assertThat(maturityStatus(sourceKey, "MILD")).isEqualTo("OPEN");

        // 같은 날 두 번째 조회에서는 재계산하지 않는다 - 일부러 CLOSE로 바꾼 행이 그대로 남는다
        jdbc.sql("UPDATE alert_item SET status = 'CLOSE' WHERE source_type = 'MATURITY' AND source_key = :key")
                .param("key", sourceKey).update();
        mockMvc.perform(get("/api/alerts")).andExpect(status().isOk());
        assertThat(maturityStatus(sourceKey, "MILD")).isEqualTo("CLOSE");

        // 날짜가 바뀌면 그날 첫 조회에서 다시 계산한다
        clock.setToday(dueDate.minusDays(5));
        mockMvc.perform(get("/api/alerts")).andExpect(status().isOk());
        assertThat(maturityStatus(sourceKey, "URGENT")).isEqualTo("OPEN");
    }

    @Test
    void dismiss_hidesAlertAndIsIdempotentKeepingTheFirstDismissedTime() throws Exception {
        startQuietDay();
        int alertId = insertAlert("JIRA", "APM-1", "DEFAULT", "OPEN", "N", null);

        mockMvc.perform(post("/api/alerts/{alertId}/dismiss", alertId)).andExpect(status().isNoContent());

        assertThat(dismissedYn(alertId)).isEqualTo("Y");
        assertThat(dismissedAt(alertId)).isEqualTo(QUIET_DAY.atTime(10, 0));
        String body = mockMvc.perform(get("/api/alerts")).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(body, "$[*].alertId")).doesNotContain(alertId);

        // 다시 닫아도 204이고 닫은 시각은 처음 값 그대로다
        clock.setToday(QUIET_DAY.plusDays(1));
        mockMvc.perform(post("/api/alerts/{alertId}/dismiss", alertId)).andExpect(status().isNoContent());
        assertThat(dismissedAt(alertId)).isEqualTo(QUIET_DAY.atTime(10, 0));
    }

    @Test
    void dismiss_unknownIdIs404AndNonNumericIdIs400() throws Exception {
        mockMvc.perform(post("/api/alerts/{alertId}/dismiss", Integer.MAX_VALUE))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/alerts/abc/dismiss"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dismissAll_dismissesOnlyVisibleAlerts() throws Exception {
        startQuietDay();
        int visible1 = insertAlert("MATURITY", "t-1", "OVERDUE", "OPEN", "N", LocalDate.of(1899, 1, 1));
        int visible2 = insertAlert("TODOIST", "123", "DEFAULT", "OPEN", "N", null);
        int closed = insertAlert("MATURITY", "t-2", "MILD", "CLOSE", "N", LocalDate.of(1900, 1, 25));

        String body = mockMvc.perform(post("/api/alerts/dismiss-all")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<Integer>read(body, "$.dismissedCount")).isEqualTo(2);
        assertThat(dismissedYn(visible1)).isEqualTo("Y");
        assertThat(dismissedYn(visible2)).isEqualTo("Y");
        assertThat(dismissedYn(closed)).isEqualTo("N");

        String again = mockMvc.perform(post("/api/alerts/dismiss-all")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Integer>read(again, "$.dismissedCount")).isZero();
    }

    @Test
    void refresh_recalculatesMaturityAndReportsJiraTodoistAsSkipped() throws Exception {
        MaturityTarget target = anyMaturityTarget();
        clock.setToday(target.lastDueDate().toLocalDate().minusDays(20));

        String body = mockMvc.perform(post("/api/alerts/refresh")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(body, "$.maturity.status")).isEqualTo("OK");
        assertThat(JsonPath.<Integer>read(body, "$.maturity.open")).isPositive();
        assertThat(JsonPath.<Integer>read(body, "$.maturity.closed")).isZero();
        assertThat(maturityStatus(String.valueOf(target.investId()), "MILD")).isEqualTo("OPEN");

        // OK가 아닌 소스는 건수 없이 status와 reason만 내려간다
        assertThat(JsonPath.<Map<String, Object>>read(body, "$.jira"))
                .containsExactlyInAnyOrderEntriesOf(Map.of("status", "SKIPPED", "reason", "not configured"));
        assertThat(JsonPath.<Map<String, Object>>read(body, "$.todoist"))
                .containsExactlyInAnyOrderEntriesOf(Map.of("status", "SKIPPED", "reason", "not configured"));
    }

    /** 만기 알림이 하나도 생기지 않는 날로 옮기고 그날 계산을 마쳐 둔다(이후 조회에서 재계산하지 않는다). */
    private void startQuietDay() {
        clock.setToday(QUIET_DAY);
        maturityRefreshTracker.refreshNow();
    }

    private MaturityTarget anyMaturityTarget() {
        List<MaturityTarget> targets = maturityTargetRepository.findActiveWithDueDate();
        assumeThat(targets).as("dev DB에 보유 중이고 만기일이 있는 투자건이 있어야 한다").isNotEmpty();
        return targets.getFirst();
    }

    private int insertAlert(String sourceType, String sourceKey, String tier, String status, String dismissedYn,
                            LocalDate dueDate) {
        jdbc.sql("""
                        INSERT INTO alert_item (source_type, source_key, tier, status, title, detail_url, due_date,
                                                dismissed_yn, upd_date)
                        VALUES (:sourceType, :sourceKey, :tier, :status, :title, :detailUrl, :dueDate, :dismissedYn,
                                NOW())
                        """)
                .param("sourceType", sourceType)
                .param("sourceKey", sourceKey)
                .param("tier", tier)
                .param("status", status)
                .param("title", "title " + sourceKey)
                .param("detailUrl", "JIRA".equals(sourceType) ? "https://example.test/browse/" + sourceKey : null)
                .param("dueDate", dueDate == null ? null : dueDate.atStartOfDay())
                .param("dismissedYn", dismissedYn)
                .update();
        return jdbc.sql("SELECT LAST_INSERT_ID()").query(Integer.class).single();
    }

    private String maturityStatus(String sourceKey, String tier) {
        return jdbc.sql("""
                        SELECT status FROM alert_item
                        WHERE source_type = 'MATURITY' AND source_key = :key AND tier = :tier
                        """)
                .param("key", sourceKey)
                .param("tier", tier)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private String dismissedYn(int alertId) {
        return jdbc.sql("SELECT dismissed_yn FROM alert_item WHERE alert_id = :alertId")
                .param("alertId", alertId).query(String.class).single();
    }

    private LocalDateTime dismissedAt(int alertId) {
        return jdbc.sql("SELECT dismissed_at FROM alert_item WHERE alert_id = :alertId")
                .param("alertId", alertId).query(LocalDateTime.class).single();
    }
}
