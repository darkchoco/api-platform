package darkchoco.apmapi.alert.service;

import darkchoco.apmapi.alert.domain.RefreshCount;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * 만기 알림 재계산을 하루 1회 보장한다(결정 A). 서비스는 오래 켜져 있으므로 기동 시에만 계산하면
 * MILD → URGENT → OVERDUE 반영이 며칠씩 늦는다. 마지막으로 성공한 계산 날짜(app.timezone)를 메모리에 두고,
 * 날짜가 바뀌었으면 그날 첫 조회 때 다시 계산한다. 재기동하면 기동 시 계산이 다시 돌기 때문에 저장하지 않는다.
 *
 * <p>재계산은 모두 이 클래스를 거친다(기동 시, 목록 조회, 수동 새로고침). 동시에 들어온 요청이 같은 날 두 번
 * 계산하지 않도록 synchronized로 한 번에 하나만 실행한다(서버는 한 대다).
 */
@Component
public class MaturityRefreshTracker {

    private final MaturityAlertService maturityAlertService;
    private final Clock clock;

    private LocalDate lastRefreshDate;

    public MaturityRefreshTracker(MaturityAlertService maturityAlertService, Clock clock) {
        this.maturityAlertService = maturityAlertService;
        this.clock = clock;
    }

    /** 지금 바로 재계산한다. 성공하면 오늘을 마지막 계산 날짜로 기록한다. */
    public synchronized RefreshCount refreshNow() {
        LocalDate today = LocalDate.now(clock);
        RefreshCount count = maturityAlertService.refresh();
        lastRefreshDate = today;
        return count;
    }

    /** 오늘 아직 계산하지 않았으면 재계산한다. */
    public synchronized void refreshIfNotDoneToday() {
        if (!LocalDate.now(clock).equals(lastRefreshDate)) {
            refreshNow();
        }
    }
}
