package darkchoco.apmapi.alert.service;

import darkchoco.apmapi.alert.domain.AlertSourceType;
import darkchoco.apmapi.alert.domain.AlertTier;
import darkchoco.apmapi.alert.domain.RefreshCount;
import darkchoco.apmapi.alert.repository.AlertItemRepository;
import darkchoco.apmapi.alert.repository.MaturityTarget;
import darkchoco.apmapi.alert.repository.MaturityTargetRepository;
import darkchoco.apmapi.alert.repository.OpenAlert;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 만기일(MATURITY) 알림 재계산. 보유 중인 투자건의 만기일로 단계를 정해 alert_item에 반영한다.
 * 한 번의 호출이 트랜잭션 하나이고, 같은 날 몇 번을 호출해도 결과가 같다(멱등).
 */
@Service
public class MaturityAlertService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaturityAlertService.class);

    private final MaturityTargetRepository maturityTargetRepository;
    private final AlertItemRepository alertItemRepository;
    private final Clock clock;

    public MaturityAlertService(MaturityTargetRepository maturityTargetRepository,
                                AlertItemRepository alertItemRepository, Clock clock) {
        this.maturityTargetRepository = maturityTargetRepository;
        this.alertItemRepository = alertItemRepository;
        this.clock = clock;
    }

    /**
     * 1. 대상 투자건마다 현재 단계를 계산해 (MATURITY, invest_id, 단계)를 OPEN으로 upsert한다.
     * 2. 그 밖의 열린 MATURITY 행은 모두 CLOSE한다 - 단계가 바뀐 건의 이전 단계 행, 보유 종료·만기일 삭제·30일 초과가 된 건.
     * 단계가 올라가면(MILD → URGENT) 기존 행을 되살리지 않고 새 단계의 행이 생기므로, 사용자가 닫았던 알림도 다시 뜬다.
     */
    @Transactional
    public RefreshCount refresh() {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = now();

        Set<AlertKey> currentAlerts = new HashSet<>();
        for (MaturityTarget target : maturityTargetRepository.findActiveWithDueDate()) {
            // last_due_date는 DATETIME이지만 날짜 부분만 쓴다.
            long daysLeft = ChronoUnit.DAYS.between(today, target.lastDueDate().toLocalDate());
            Optional<AlertTier> tier = AlertTier.forMaturity(daysLeft);
            if (tier.isEmpty()) {
                continue;
            }

            String sourceKey = String.valueOf(target.investId());
            alertItemRepository.upsertOpen(AlertSourceType.MATURITY, sourceKey, tier.get(), target.alertTitle(),
                    null, target.lastDueDate(), now);
            currentAlerts.add(new AlertKey(sourceKey, tier.get()));
        }

        List<Integer> staleAlertIds = alertItemRepository.findOpen(AlertSourceType.MATURITY).stream()
                .filter(open -> !currentAlerts.contains(new AlertKey(open.sourceKey(), open.tier())))
                .map(OpenAlert::alertId)
                .toList();
        int closed = alertItemRepository.close(staleAlertIds, now);

        LOGGER.info("만기 알림 재계산: today={}, open={}, closed={}", today, currentAlerts.size(), closed);
        return new RefreshCount(currentAlerts.size(), closed);
    }

    // 초 미만은 버린다 - DATETIME에 넣을 때 DB가 반올림하는 것을 피한다.
    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    private record AlertKey(String sourceKey, AlertTier tier) {
    }
}
