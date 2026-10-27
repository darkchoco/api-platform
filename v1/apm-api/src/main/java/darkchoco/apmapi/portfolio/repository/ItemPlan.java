package darkchoco.apmapi.portfolio.repository;

import java.math.BigDecimal;

/** portfolio_item_plan 한 행. 종목의 통화 컬럼에만 값이 들어 있다. */
public record ItemPlan(int productId, Long planKrw, BigDecimal planUsd, BigDecimal planEur) {
}
