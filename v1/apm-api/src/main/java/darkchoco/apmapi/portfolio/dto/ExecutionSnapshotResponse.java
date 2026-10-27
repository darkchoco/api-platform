package darkchoco.apmapi.portfolio.dto;

import darkchoco.apmapi.portfolio.domain.FxSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * '값 가져오기' 결과. DB에 저장되지 않는 조회용 값이다.
 *
 * @param baseDate  집계에 쓴 최신 기준일
 * @param fxDate    EX_RATE일 때 실제 사용한 ex_rate.base_date, MANUAL이면 null
 * @param fetchDate 이 값을 가져온(계산한) 시각
 * @param exec      대상 전부(schemeAcId 순), 보유 건이 없는 대상은 0
 */
public record ExecutionSnapshotResponse(
        LocalDate baseDate,
        BigDecimal fxUsd,
        BigDecimal fxEur,
        FxSource fxSource,
        LocalDate fxDate,
        LocalDateTime fetchDate,
        List<ExecAmountDto> exec) {
}
