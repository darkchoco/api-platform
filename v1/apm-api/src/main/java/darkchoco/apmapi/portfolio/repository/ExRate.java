package darkchoco.apmapi.portfolio.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/** ex_rate 한 행. usd·eur가 모두 있는 행만 다룬다. */
public record ExRate(LocalDate baseDate, BigDecimal usd, BigDecimal eur) {
}
