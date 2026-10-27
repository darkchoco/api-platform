package darkchoco.apmapi.portfolio.repository;

import darkchoco.apmapi.portfolio.domain.HoldingCurrency;
import java.math.BigDecimal;

/**
 * 종목 × 보유 통화 1건의 최신 기준월 시가. 같은 종목을 여러 계좌에 가진 경우 이미 합산되어 있다.
 * 해당 통화의 컬럼에만 값이 있고 나머지 통화는 0이다.
 */
public record HoldingRow(
        int productId,
        int schemeAcId,
        String productNm,
        String symbolAlias,
        String productNmDetail,
        HoldingCurrency currency,
        long currentKrw,
        BigDecimal currentUsd,
        BigDecimal currentEur) {
}
