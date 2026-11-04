package darkchoco.apmapi.alert.service;

import darkchoco.apmapi.alert.dto.AlertRefreshResponse;
import darkchoco.apmapi.alert.dto.AlertResponse;
import darkchoco.apmapi.alert.dto.DismissAllResponse;
import darkchoco.apmapi.alert.dto.SourceRefreshResult;
import darkchoco.apmapi.alert.external.JiraAlertSource;
import darkchoco.apmapi.alert.external.TodoistAlertSource;
import darkchoco.apmapi.alert.repository.AlertItemRepository;
import darkchoco.apmapi.alert.repository.VisibleAlert;
import darkchoco.apmapi.common.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 알림 조회 · 닫기 · 수동 새로고침(ALERT-BE-3). */
@Service
public class AlertService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertService.class);

    // 심각도(AlertTier 선언 순서) → 날짜 빠른 순(날짜 없음은 마지막) → alertId
    private static final Comparator<VisibleAlert> DISPLAY_ORDER = Comparator
            .comparing(VisibleAlert::tier)
            .thenComparing(VisibleAlert::dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingInt(VisibleAlert::alertId);

    private final AlertItemRepository alertItemRepository;
    private final MaturityRefreshTracker maturityRefreshTracker;
    private final ExternalAlertSyncService externalAlertSyncService;
    private final JiraAlertSource jiraAlertSource;
    private final TodoistAlertSource todoistAlertSource;
    private final Clock clock;

    public AlertService(AlertItemRepository alertItemRepository, MaturityRefreshTracker maturityRefreshTracker,
                        ExternalAlertSyncService externalAlertSyncService, JiraAlertSource jiraAlertSource,
                        TodoistAlertSource todoistAlertSource, Clock clock) {
        this.alertItemRepository = alertItemRepository;
        this.maturityRefreshTracker = maturityRefreshTracker;
        this.externalAlertSyncService = externalAlertSyncService;
        this.jiraAlertSource = jiraAlertSource;
        this.todoistAlertSource = todoistAlertSource;
        this.clock = clock;
    }

    /**
     * 표시할 알림 목록. 그날 첫 조회라면 만기 알림을 먼저 재계산한다.
     * 재계산이 실패해도 목록은 지금 DB 기준으로 돌려준다(다음 조회 때 다시 시도한다).
     */
    public List<AlertResponse> getAlerts() {
        try {
            maturityRefreshTracker.refreshIfNotDoneToday();
        } catch (RuntimeException e) {
            LOGGER.error("조회 전 만기 알림 재계산 실패 - 기존 알림으로 응답한다", e);
        }

        LocalDate today = LocalDate.now(clock);
        return alertItemRepository.findVisible().stream()
                .sorted(DISPLAY_ORDER)
                .map(alert -> toResponse(alert, today))
                .toList();
    }

    /** 알림 하나를 닫는다. 이미 닫혀 있으면 아무것도 하지 않는다(멱등). */
    @Transactional
    public void dismiss(int alertId) {
        int dismissed = alertItemRepository.dismiss(alertId, now());
        if (dismissed == 0 && !alertItemRepository.exists(alertId)) {
            throw new ResourceNotFoundException("알림을 찾을 수 없습니다: " + alertId);
        }
    }

    /** 지금 표시되는 알림을 모두 닫는다. */
    @Transactional
    public DismissAllResponse dismissAll() {
        return new DismissAllResponse(alertItemRepository.dismissAllVisible(now()));
    }

    /** 모든 소스를 즉시 재계산(재조회)한다. 한 소스의 실패는 결과에만 담고 예외로 올리지 않는다. */
    public AlertRefreshResponse refreshAll() {
        return new AlertRefreshResponse(
                refreshMaturity(),
                externalAlertSyncService.sync(jiraAlertSource),
                externalAlertSyncService.sync(todoistAlertSource));
    }

    private SourceRefreshResult refreshMaturity() {
        try {
            return SourceRefreshResult.ok(maturityRefreshTracker.refreshNow());
        } catch (RuntimeException e) {
            LOGGER.error("수동 새로고침: 만기 알림 재계산 실패", e);
            // 응답에는 내부 오류 내용(SQL 등)을 싣지 않는다.
            return SourceRefreshResult.failed("maturity refresh failed");
        }
    }

    private static AlertResponse toResponse(VisibleAlert alert, LocalDate today) {
        LocalDate dueDate = alert.dueDate() == null ? null : alert.dueDate().toLocalDate();
        Long daysLeft = dueDate == null ? null : ChronoUnit.DAYS.between(today, dueDate);
        return new AlertResponse(alert.alertId(), alert.sourceType(), alert.tier(), alert.title(),
                alert.detailUrl(), dueDate, daysLeft);
    }

    // 초 미만은 버린다 - DATETIME에 넣을 때 DB가 반올림하는 것을 피한다.
    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
