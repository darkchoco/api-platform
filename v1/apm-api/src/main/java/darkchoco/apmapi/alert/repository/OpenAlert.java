package darkchoco.apmapi.alert.repository;

import darkchoco.apmapi.alert.domain.AlertTier;

/** status='OPEN'인 alert_item 행의 식별 정보. */
public record OpenAlert(int alertId, String sourceKey, AlertTier tier) {
}
