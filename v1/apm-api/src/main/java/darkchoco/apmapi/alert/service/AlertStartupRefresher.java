package darkchoco.apmapi.alert.service;

import darkchoco.apmapi.alert.external.JiraAlertSource;
import darkchoco.apmapi.alert.external.TodoistAlertSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 서비스 기동 시 알림을 1회 재계산한다: 만기일 계산, Jira/Todoist 조회. 실패해도 기동을 막지 않는다(로그만 남긴다).
 * Jira/Todoist는 기동 시와 수동 새로고침 때만 가져온다(별도 주기 스케줄 없음).
 */
@Component
@ConditionalOnBooleanProperty("app.alert.refresh-on-startup")
public class AlertStartupRefresher {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertStartupRefresher.class);

    private final MaturityRefreshTracker maturityRefreshTracker;
    private final ExternalAlertSyncService externalAlertSyncService;
    private final JiraAlertSource jiraAlertSource;
    private final TodoistAlertSource todoistAlertSource;

    public AlertStartupRefresher(MaturityRefreshTracker maturityRefreshTracker,
                                 ExternalAlertSyncService externalAlertSyncService, JiraAlertSource jiraAlertSource,
                                 TodoistAlertSource todoistAlertSource) {
        this.maturityRefreshTracker = maturityRefreshTracker;
        this.externalAlertSyncService = externalAlertSyncService;
        this.jiraAlertSource = jiraAlertSource;
        this.todoistAlertSource = todoistAlertSource;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void refreshOnStartup() {
        // 계산 날짜를 기록해 두어야 같은 날 첫 조회에서 다시 계산하지 않는다.
        try {
            maturityRefreshTracker.refreshNow();
        } catch (RuntimeException e) {
            LOGGER.error("기동 시 만기 알림 재계산 실패 - 기동은 계속한다", e);
        }

        // sync는 예외를 던지지 않고 결과만 돌려주며, 실패 로그도 그 안에서 남긴다.
        externalAlertSyncService.sync(jiraAlertSource);
        externalAlertSyncService.sync(todoistAlertSource);
    }
}
