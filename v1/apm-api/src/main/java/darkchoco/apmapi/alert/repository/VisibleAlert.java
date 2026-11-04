package darkchoco.apmapi.alert.repository;

import darkchoco.apmapi.alert.domain.AlertSourceType;
import darkchoco.apmapi.alert.domain.AlertTier;
import java.time.LocalDateTime;

/** 화면에 표시할 알림(status='OPEN' AND dismissed_yn='N') 한 건. */
public record VisibleAlert(int alertId, AlertSourceType sourceType, AlertTier tier, String title, String detailUrl,
                           LocalDateTime dueDate) {
}
