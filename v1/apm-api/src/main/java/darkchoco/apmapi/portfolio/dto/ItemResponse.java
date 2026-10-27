package darkchoco.apmapi.portfolio.dto;

import darkchoco.apmapi.portfolio.domain.HoldingCurrency;
import java.math.BigDecimal;

/**
 * 종목별 세부 현황 1건. 종목 행은 저장하지 않고 호출 시점마다 보유 건에서 구성한다.
 *
 * @param currency      종목의 통화 (보유 건의 currency_unit)
 * @param planKrw       투자계획금액. 종목의 통화 컬럼에만 값이 있고 나머지 둘은 null, 계획이 없으면 셋 다 null
 * @param currentKrw    시가(현재가치). 해당 통화의 보유분만 담기며 나머지 통화는 0
 * @param targetRatio   대상내 비율(0~1). 계획이 없거나 분모를 계산할 수 없으면 null
 */
public record ItemResponse(
        int productId,
        int schemeAcId,
        String productNm,
        HoldingCurrency currency,
        String symbolAlias,
        String productNmDetail,
        Long planKrw,
        BigDecimal planUsd,
        BigDecimal planEur,
        long currentKrw,
        BigDecimal currentUsd,
        BigDecimal currentEur,
        BigDecimal targetRatio) {
}
