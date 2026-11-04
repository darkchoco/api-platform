package darkchoco.apmapi.alert.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 서비스 기동 시 알림을 1회 재계산한다. 실패해도 기동을 막지 않는다(로그만 남긴다). */
@Component
@ConditionalOnBooleanProperty("app.alert.refresh-on-startup")
public class AlertStartupRefresher {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertStartupRefresher.class);

    private final MaturityAlertService maturityAlertService;

    public AlertStartupRefresher(MaturityAlertService maturityAlertService) {
        this.maturityAlertService = maturityAlertService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void refreshOnStartup() {
        try {
            maturityAlertService.refresh();
        } catch (RuntimeException e) {
            LOGGER.error("기동 시 만기 알림 재계산 실패 - 기동은 계속한다", e);
        }
    }
}
