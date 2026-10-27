package darkchoco.apmapi.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record VersionDetailResponse(
        int versionId,
        String versionLabel,
        Long availKrw,
        BigDecimal availUsd,
        BigDecimal availEur,
        Long planKrw,
        BigDecimal planUsd,
        BigDecimal planEur,
        BigDecimal fxUsd,
        BigDecimal fxEur,
        LocalDateTime fetchDate,
        LocalDateTime insDate,
        List<PlanDetailDto> detail) {
}
