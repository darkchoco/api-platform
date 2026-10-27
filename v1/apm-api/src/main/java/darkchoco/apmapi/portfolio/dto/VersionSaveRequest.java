package darkchoco.apmapi.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 버전 저장 요청 본문 (POST·PUT 공용). 조회 응답에서 versionId·versionLabel·insDate를 뺀 형태다.
 * versionLabel은 서버가 생성하므로 받지 않는다.
 *
 * @param baseVersionId POST 전용. 있으면 '새 버전으로 저장'(기준 버전의 라벨을 올림), 없으면 이번 달 {@code .0} 버전 생성. PUT에서는 무시한다.
 */
public record VersionSaveRequest(
        Integer baseVersionId,
        Long availKrw,
        BigDecimal availUsd,
        BigDecimal availEur,
        Long planKrw,
        BigDecimal planUsd,
        BigDecimal planEur,
        BigDecimal fxUsd,
        BigDecimal fxEur,
        LocalDateTime fetchDate,
        List<PlanDetailDto> detail) {
}
