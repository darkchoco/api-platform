package darkchoco.apmapi.alert;

import static org.assertj.core.api.Assertions.assertThat;

import darkchoco.apmapi.alert.domain.AlertSourceType;
import darkchoco.apmapi.alert.domain.SourceRefreshStatus;
import darkchoco.apmapi.alert.dto.SourceRefreshResult;
import darkchoco.apmapi.alert.external.ExternalAlert;
import darkchoco.apmapi.alert.external.ExternalAlertSource;
import darkchoco.apmapi.alert.service.ExternalAlertSyncService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Jira/Todoist 반영(ALERT-BE-2)을 실제 dev DB에 대해 검증한다. 외부 호출 대신 가짜 소스를 쓰고, 테스트마다 롤백된다.
 * dev DB(43306)와 DB_PASSWORD가 필요하다.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@Import(MaturityAlertRefreshTest.MutableClockConfig.class)
class ExternalAlertSyncTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);

    @Autowired
    private ExternalAlertSyncService syncService;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private MaturityAlertRefreshTest.MutableClock clock;

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM alert_item").update();
        clock.setToday(TODAY);
    }

    @Test
    void sync_upsertsFetchedItemsAsOpenDefaultTier() {
        SourceRefreshResult result = syncService.sync(FakeSource.returning(AlertSourceType.JIRA,
                new ExternalAlert("APM-1", "APM-1 환율 알림 확인", "https://jira.example.test/browse/APM-1",
                        LocalDate.of(2026, 11, 20)),
                new ExternalAlert("APM-2", "APM-2 기한 없음", "https://jira.example.test/browse/APM-2", null)));

        assertThat(result).isEqualTo(new SourceRefreshResult(SourceRefreshStatus.OK, 2, 0, null));
        AlertRow row = alertRow("JIRA", "APM-1").orElseThrow();
        assertThat(row.tier()).isEqualTo("DEFAULT");
        assertThat(row.status()).isEqualTo("OPEN");
        assertThat(row.dismissedYn()).isEqualTo("N");
        assertThat(row.title()).isEqualTo("APM-1 환율 알림 확인");
        assertThat(row.detailUrl()).isEqualTo("https://jira.example.test/browse/APM-1");
        assertThat(row.dueDate()).isEqualTo(LocalDateTime.of(2026, 11, 20, 0, 0));
        assertThat(row.updDate()).isEqualTo(TODAY.atTime(10, 0));
        assertThat(alertRow("JIRA", "APM-2").orElseThrow().dueDate()).isNull();
    }

    @Test
    void sync_closesItemsMissingFromTheFetchAndKeepsDismissal() {
        syncService.sync(FakeSource.returning(AlertSourceType.TODOIST,
                new ExternalAlert("t1", "첫 태스크", "https://app.todoist.com/app/task/t1", null),
                new ExternalAlert("t2", "둘째 태스크", "https://app.todoist.com/app/task/t2", null)));
        jdbc.sql("UPDATE alert_item SET dismissed_yn = 'Y' WHERE source_key = 't1'").update();

        // t1은 제목이 바뀌어 다시 오고, t2는 완료되어 빠졌다
        SourceRefreshResult result = syncService.sync(FakeSource.returning(AlertSourceType.TODOIST,
                new ExternalAlert("t1", "첫 태스크(수정)", "https://app.todoist.com/app/task/t1", null)));

        assertThat(result).isEqualTo(new SourceRefreshResult(SourceRefreshStatus.OK, 1, 1, null));
        AlertRow t1 = alertRow("TODOIST", "t1").orElseThrow();
        assertThat(t1.status()).isEqualTo("OPEN");
        assertThat(t1.title()).isEqualTo("첫 태스크(수정)");
        assertThat(t1.dismissedYn()).as("사용자가 닫은 알림은 다시 띄우지 않는다").isEqualTo("Y");
        assertThat(alertRow("TODOIST", "t2").orElseThrow().status()).isEqualTo("CLOSE");
    }

    @Test
    void sync_onFetchFailureKeepsExistingAlertsUntouched() {
        syncService.sync(FakeSource.returning(AlertSourceType.JIRA,
                new ExternalAlert("APM-1", "APM-1 그대로", "https://jira.example.test/browse/APM-1", null)));

        SourceRefreshResult result = syncService.sync(FakeSource.failing(AlertSourceType.JIRA));

        assertThat(result).isEqualTo(SourceRefreshResult.failed("fetch failed"));
        assertThat(alertRow("JIRA", "APM-1").orElseThrow().status()).isEqualTo("OPEN");
    }

    @Test
    void sync_skipsUnconfiguredSourceWithoutTouchingDb() {
        syncService.sync(FakeSource.returning(AlertSourceType.JIRA,
                new ExternalAlert("APM-1", "APM-1", "https://jira.example.test/browse/APM-1", null)));

        SourceRefreshResult result = syncService.sync(FakeSource.notConfigured(AlertSourceType.JIRA));

        assertThat(result).isEqualTo(SourceRefreshResult.skipped("not configured"));
        assertThat(alertRow("JIRA", "APM-1").orElseThrow().status()).isEqualTo("OPEN");
    }

    @Test
    void sync_onlyClosesItemsOfTheSameSource() {
        syncService.sync(FakeSource.returning(AlertSourceType.JIRA,
                new ExternalAlert("APM-1", "APM-1", "https://jira.example.test/browse/APM-1", null)));

        // Todoist가 빈 목록이어도 Jira 알림은 닫지 않는다
        syncService.sync(FakeSource.returning(AlertSourceType.TODOIST));

        assertThat(alertRow("JIRA", "APM-1").orElseThrow().status()).isEqualTo("OPEN");
    }

    private Optional<AlertRow> alertRow(String sourceType, String sourceKey) {
        return jdbc.sql("""
                        SELECT tier, status, title, detail_url, due_date, dismissed_yn, upd_date
                        FROM alert_item
                        WHERE source_type = :sourceType AND source_key = :sourceKey
                        """)
                .param("sourceType", sourceType)
                .param("sourceKey", sourceKey)
                .query((rs, rowNum) -> new AlertRow(
                        rs.getString("tier"),
                        rs.getString("status"),
                        rs.getString("title"),
                        rs.getString("detail_url"),
                        rs.getObject("due_date", LocalDateTime.class),
                        rs.getString("dismissed_yn"),
                        rs.getObject("upd_date", LocalDateTime.class)))
                .optional();
    }

    private record AlertRow(String tier, String status, String title, String detailUrl, LocalDateTime dueDate,
                            String dismissedYn, LocalDateTime updDate) {
    }

    /** 외부 호출 없이 정해진 결과를 돌려주는 소스. */
    private record FakeSource(AlertSourceType sourceType, boolean isConfigured, List<ExternalAlert> alerts)
            implements ExternalAlertSource {

        static FakeSource returning(AlertSourceType sourceType, ExternalAlert... alerts) {
            return new FakeSource(sourceType, true, List.of(alerts));
        }

        static FakeSource failing(AlertSourceType sourceType) {
            return new FakeSource(sourceType, true, null);
        }

        static FakeSource notConfigured(AlertSourceType sourceType) {
            return new FakeSource(sourceType, false, List.of());
        }

        @Override
        public List<ExternalAlert> fetchOpenAlerts() {
            if (alerts == null) {
                throw new IllegalStateException("외부 서비스 응답 없음");
            }
            return alerts;
        }
    }
}
