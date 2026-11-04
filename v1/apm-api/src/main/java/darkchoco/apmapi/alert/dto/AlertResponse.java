package darkchoco.apmapi.alert.dto;

import darkchoco.apmapi.alert.domain.AlertSourceType;
import darkchoco.apmapi.alert.domain.AlertTier;
import java.time.LocalDate;

/**
 * 알림 목록(GET /api/alerts)의 한 건.
 *
 * @param dueDate  만기일·마감일(날짜만). 없으면 null
 * @param daysLeft dueDate − 오늘(app.timezone) 일수, 음수 = 경과. dueDate가 없으면 null.
 *                 프론트와 서버의 "오늘"이 어긋나지 않도록 서버가 계산해 내려준다
 */
public record AlertResponse(
        int alertId,
        AlertSourceType sourceType,
        AlertTier tier,
        String title,
        String detailUrl,
        LocalDate dueDate,
        Long daysLeft) {
}
