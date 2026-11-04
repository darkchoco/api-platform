package darkchoco.apmapi.alert.service;

import darkchoco.apmapi.alert.domain.AlertSourceType;
import darkchoco.apmapi.alert.domain.AlertTier;
import darkchoco.apmapi.alert.domain.RefreshCount;
import darkchoco.apmapi.alert.dto.SourceRefreshResult;
import darkchoco.apmapi.alert.external.ExternalAlert;
import darkchoco.apmapi.alert.external.ExternalAlertSource;
import darkchoco.apmapi.alert.repository.AlertItemRepository;
import darkchoco.apmapi.alert.repository.OpenAlert;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Jira/Todoist 항목을 alert_item에 반영한다(ALERT-BE-2). 소스마다 독립적으로 실행하며, 이 메서드는 예외를 던지지 않고
 * 결과(OK/FAILED/SKIPPED)만 돌려준다 - 한 소스의 실패가 다른 소스나 기동에 영향을 주지 않게 하기 위함이다.
 */
@Service
public class ExternalAlertSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalAlertSyncService.class);

    private final AlertItemRepository alertItemRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public ExternalAlertSyncService(AlertItemRepository alertItemRepository, TransactionTemplate transactionTemplate,
                                    Clock clock) {
        this.alertItemRepository = alertItemRepository;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    /**
     * 1. 설정이 없으면 건너뛴다(SKIPPED).
     * 2. 외부 소스를 호출한다. 실패하면 그 소스의 기존 알림은 건드리지 않는다(FAILED) - 알림이 갑자기 사라지지 않게.
     * 3. 성공했을 때만 한 트랜잭션으로 반영한다: 가져온 항목은 OPEN으로 upsert, 목록에 없는 열린 항목은 CLOSE.
     */
    public SourceRefreshResult sync(ExternalAlertSource source) {
        AlertSourceType sourceType = source.sourceType();
        if (!source.isConfigured()) {
            LOGGER.info("{} 알림 소스 비활성 - 설정 없음", sourceType);
            return SourceRefreshResult.skipped("not configured");
        }

        List<ExternalAlert> alerts;
        try {
            alerts = source.fetchOpenAlerts();
        } catch (RuntimeException e) {
            // 토큰이 로그에 남지 않도록 스택트레이스 없이 예외 요약만 남긴다(요청 헤더는 예외 메시지에 들어가지 않는다).
            LOGGER.warn("{} 알림 조회 실패 - 기존 알림을 유지한다: {}", sourceType, e.toString());
            return SourceRefreshResult.failed("fetch failed");
        }

        try {
            RefreshCount count = transactionTemplate.execute(status -> replaceOpenAlerts(sourceType, alerts));
            LOGGER.info("{} 알림 반영: open={}, closed={}", sourceType, count.open(), count.closed());
            return SourceRefreshResult.ok(count);
        } catch (RuntimeException e) {
            LOGGER.error("{} 알림 저장 실패", sourceType, e);
            return SourceRefreshResult.failed("save failed");
        }
    }

    private RefreshCount replaceOpenAlerts(AlertSourceType sourceType, List<ExternalAlert> alerts) {
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);

        Set<String> fetchedKeys = new HashSet<>();
        for (ExternalAlert alert : alerts) {
            alertItemRepository.upsertOpen(sourceType, alert.sourceKey(), AlertTier.DEFAULT, alert.title(),
                    alert.detailUrl(), toDateTime(alert.dueDate()), now);
            fetchedKeys.add(alert.sourceKey());
        }

        // 이슈 완료·삭제·라벨 제거로 목록에서 빠진 항목
        List<Integer> staleAlertIds = alertItemRepository.findOpen(sourceType).stream()
                .filter(open -> !fetchedKeys.contains(open.sourceKey()))
                .map(OpenAlert::alertId)
                .toList();
        int closed = alertItemRepository.close(staleAlertIds, now);

        return new RefreshCount(fetchedKeys.size(), closed);
    }

    private static LocalDateTime toDateTime(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }
}
