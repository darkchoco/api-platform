package darkchoco.apmapi.portfolio.dto;

import java.math.BigDecimal;

/** 대상별 실행 금액. 보유 통화에 해당하는 컬럼만 합산한 값이며, 보유 건이 없으면 0이다. */
public record ExecAmountDto(int schemeAcId, long execKrw, BigDecimal execUsd, BigDecimal execEur) {
}
