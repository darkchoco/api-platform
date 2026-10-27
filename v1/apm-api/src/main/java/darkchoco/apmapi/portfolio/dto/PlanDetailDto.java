package darkchoco.apmapi.portfolio.dto;

import java.math.BigDecimal;

/**
 * 버전 × 대상 1건. 조회 응답과 저장 요청이 같은 형태를 쓴다.
 * 비율은 0~1 소수, exec*는 '값 가져오기' 전이면 null.
 */
public record PlanDetailDto(
        int schemeAcId,
        BigDecimal krwUsdRatio,
        BigDecimal eurRatio,
        Long execKrw,
        BigDecimal execUsd,
        BigDecimal execEur) {
}
