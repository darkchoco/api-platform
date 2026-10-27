package darkchoco.apmapi.portfolio.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ExRateRepository {

    private final JdbcClient jdbc;

    public ExRateRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** from~to(양 끝 날짜 포함) 사이의 환율. USD·EUR 중 하나라도 비어 있는 행은 쓸 수 없으므로 제외한다. */
    public List<ExRate> findBetween(LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT base_date, usd, eur
                          FROM ex_rate
                         WHERE base_date >= :from AND base_date < :toExclusive
                           AND usd IS NOT NULL AND eur IS NOT NULL
                         ORDER BY base_date
                        """)
                .param("from", from.atStartOfDay())
                .param("toExclusive", to.plusDays(1).atStartOfDay())
                .query((rs, rowNum) -> new ExRate(
                        rs.getObject("base_date", LocalDateTime.class).toLocalDate(),
                        rs.getBigDecimal("usd"),
                        rs.getBigDecimal("eur")))
                .list();
    }
}
