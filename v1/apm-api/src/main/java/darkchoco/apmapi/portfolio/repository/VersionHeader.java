package darkchoco.apmapi.portfolio.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** portfolio_version 한 행. 대상별 detail은 별도로 조회한다. */
public record VersionHeader(
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
        LocalDateTime updDate) {
}
