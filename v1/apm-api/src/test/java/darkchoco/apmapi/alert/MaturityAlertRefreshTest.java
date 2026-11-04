package darkchoco.apmapi.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import darkchoco.apmapi.alert.domain.RefreshCount;
import darkchoco.apmapi.alert.repository.MaturityTarget;
import darkchoco.apmapi.alert.repository.MaturityTargetRepository;
import darkchoco.apmapi.alert.service.AlertStartupRefresher;
import darkchoco.apmapi.alert.service.MaturityAlertService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 만기 알림 재계산(ALERT-BE-1)을 실제 dev DB에 대해 검증한다. 테스트마다 롤백되어 dev 데이터를 남기지 않는다.
 * investment는 바꾸지 않고, 만기일이 있는 투자건 하나를 골라 "오늘"(시계)을 그 만기일 기준으로 옮겨 가며 확인한다.
 * dev DB(43306)와 DB_PASSWORD가 필요하다.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@Import(MaturityAlertRefreshTest.MutableClockConfig.class)
class MaturityAlertRefreshTest {

    @Autowired
    private MaturityAlertService maturityAlertService;
    @Autowired
    private MaturityTargetRepository maturityTargetRepository;
    @Autowired
    private MutableClock clock;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private ApplicationContext context;

    private MaturityTarget target;
    private LocalDate dueDate;

    @BeforeEach
    void pickTarget() {
        List<MaturityTarget> targets = maturityTargetRepository.findActiveWithDueDate();
        assumeThat(targets).as("dev DB에 보유 중이고 만기일이 있는 투자건이 있어야 한다").isNotEmpty();
        target = targets.getFirst();
        dueDate = target.lastDueDate().toLocalDate();
    }

    @Test
    void refresh_insertsOpenAlertWithTitleAndDueDate() {
        clock.setToday(dueDate.minusDays(20));

        RefreshCount count = maturityAlertService.refresh();

        AlertRow row = alertRow("MILD").orElseThrow();
        assertThat(row.status()).isEqualTo("OPEN");
        assertThat(row.dismissedYn()).isEqualTo("N");
        assertThat(row.title()).isEqualTo(expectedTitle());
        assertThat(row.detailUrl()).isNull();
        assertThat(row.dueDate()).isEqualTo(target.lastDueDate());
        assertThat(row.updDate()).isEqualTo(dueDate.minusDays(20).atTime(10, 0));
        assertThat(count.open()).isPositive();
    }

    @Test
    void refresh_escalatesByAddingNewTierRowAndClosingThePreviousOne() {
        clock.setToday(dueDate.minusDays(20));
        maturityAlertService.refresh();

        clock.setToday(dueDate.minusDays(5));
        maturityAlertService.refresh();
        assertThat(alertRow("MILD").orElseThrow().status()).isEqualTo("CLOSE");
        assertThat(alertRow("URGENT").orElseThrow().status()).isEqualTo("OPEN");

        clock.setToday(dueDate.plusDays(1));
        maturityAlertService.refresh();
        assertThat(alertRow("URGENT").orElseThrow().status()).isEqualTo("CLOSE");
        AlertRow overdue = alertRow("OVERDUE").orElseThrow();
        assertThat(overdue.status()).isEqualTo("OPEN");
        assertThat(overdue.dismissedYn()).isEqualTo("N");
    }

    @Test
    void refresh_keepsUserDismissalWhenStillOpenAndWhenClosed() {
        clock.setToday(dueDate.minusDays(20));
        maturityAlertService.refresh();
        jdbc.sql("UPDATE alert_item SET dismissed_yn = 'Y' WHERE alert_id = :alertId")
                .param("alertId", alertRow("MILD").orElseThrow().alertId())
                .update();

        // 같은 날 다시 계산해도 사용자가 닫은 것을 되돌리지 않는다
        maturityAlertService.refresh();
        AlertRow stillOpen = alertRow("MILD").orElseThrow();
        assertThat(stillOpen.status()).isEqualTo("OPEN");
        assertThat(stillOpen.dismissedYn()).isEqualTo("Y");

        // 30일 초과가 되어 조건에서 벗어나면 CLOSE로 바뀌고 dismissed_yn은 그대로다
        clock.setToday(dueDate.minusDays(45));
        maturityAlertService.refresh();
        AlertRow closed = alertRow("MILD").orElseThrow();
        assertThat(closed.status()).isEqualTo("CLOSE");
        assertThat(closed.dismissedYn()).isEqualTo("Y");
    }

    @Test
    void refresh_noAlertWhenMoreThan30DaysLeft() {
        clock.setToday(dueDate.minusDays(31));

        maturityAlertService.refresh();

        assertThat(alertRowCount()).isZero();
    }

    @Test
    void refresh_isIdempotentOnTheSameDay() {
        clock.setToday(dueDate.minusDays(20));
        RefreshCount first = maturityAlertService.refresh();
        int rowsAfterFirst = maturityRowCount();

        RefreshCount second = maturityAlertService.refresh();

        assertThat(second).isEqualTo(new RefreshCount(first.open(), 0));
        assertThat(maturityRowCount()).isEqualTo(rowsAfterFirst);
    }

    @Test
    void startupRefresh_isDisabledInTests() {
        assertThat(context.getBeanProvider(AlertStartupRefresher.class).getIfAvailable()).isNull();
    }

    private String expectedTitle() {
        return jdbc.sql("""
                        SELECT CONCAT(p.product_nm, ' · ', f.fin_inst_nm)
                        FROM investment i
                                 JOIN product p ON p.product_id = i.product_id
                                 JOIN acct a ON a.acct_id = i.acct_id
                                 JOIN fin_inst f ON f.fin_inst_id = a.fin_inst_id
                        WHERE i.invest_id = :investId
                        """)
                .param("investId", target.investId())
                .query(String.class)
                .single();
    }

    private Optional<AlertRow> alertRow(String tier) {
        return jdbc.sql("""
                        SELECT alert_id, status, title, detail_url, due_date, dismissed_yn, upd_date
                        FROM alert_item
                        WHERE source_type = 'MATURITY' AND source_key = :sourceKey AND tier = :tier
                        """)
                .param("sourceKey", String.valueOf(target.investId()))
                .param("tier", tier)
                .query((rs, rowNum) -> new AlertRow(
                        rs.getInt("alert_id"),
                        rs.getString("status"),
                        rs.getString("title"),
                        rs.getString("detail_url"),
                        rs.getObject("due_date", LocalDateTime.class),
                        rs.getString("dismissed_yn"),
                        rs.getObject("upd_date", LocalDateTime.class)))
                .optional();
    }

    private int alertRowCount() {
        return jdbc.sql("SELECT COUNT(*) FROM alert_item WHERE source_type = 'MATURITY' AND source_key = :sourceKey")
                .param("sourceKey", String.valueOf(target.investId()))
                .query(Integer.class)
                .single();
    }

    private int maturityRowCount() {
        return jdbc.sql("SELECT COUNT(*) FROM alert_item WHERE source_type = 'MATURITY'")
                .query(Integer.class)
                .single();
    }

    private record AlertRow(int alertId, String status, String title, String detailUrl, LocalDateTime dueDate,
                            String dismissedYn, LocalDateTime updDate) {
    }

    /** 테스트가 "오늘"을 바꿀 수 있는 시계. 시각은 항상 그날 10:00:00(Europe/Berlin)이다. */
    static class MutableClock extends Clock {

        private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

        private Instant instant = Instant.now();

        void setToday(LocalDate today) {
            instant = today.atTime(10, 0).atZone(ZONE).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @TestConfiguration
    static class MutableClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }
}
