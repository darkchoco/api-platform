package darkchoco.apmapi.portfolio.repository;

import static darkchoco.apmapi.portfolio.repository.TargetRepository.PORTFOLIO_SCHEME_ID;

import darkchoco.apmapi.portfolio.dto.ExecAmountDto;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 보유 건(stock_curr, investment_curr)의 최신 기준월 실행 금액 집계. */
@Repository
public class ExecutionRepository {

    private final JdbcClient jdbc;

    public ExecutionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** stock_curr·investment_curr 각각의 MAX(base_date) 중 늦은 값. 보유 데이터가 전혀 없으면 빈 값. */
    public Optional<LocalDate> findLatestBaseDate() {
        // MAX는 NULL base_date를 무시한다. 두 테이블 모두 비어 있으면 NULL 한 건이 나온다.
        return jdbc.sql("""
                        SELECT MAX(latest)
                          FROM (SELECT MAX(base_date) AS latest FROM stock_curr
                                UNION ALL
                                SELECT MAX(base_date) FROM investment_curr) t
                        """)
                .query((rs, rowNum) -> rs.getObject(1, LocalDateTime.class))
                .optional()
                .map(LocalDateTime::toLocalDate);
    }

    /**
     * 대상별 실행 금액. 보유 건의 통화(currency_unit)에 해당하는 컬럼만 합산한다.
     * val_krw는 USD·EUR 보유 건에도 원화 환산액이 채워져 있어서 통화 구분 없이 더하면 이중 계상된다.
     * 대상에 매핑되지 않는 보유 건(연금, 보험 등)은 의도적으로 제외되고, 보유 건이 없는 대상은 결과에 없다.
     */
    public List<ExecAmountDto> findExecAmounts() {
        return jdbc.sql("""
                        SELECT sac.scheme_ac_id,
                               SUM(CASE WHEN h.cur = 'KRW' THEN h.val_krw ELSE 0 END) AS exec_krw,
                               SUM(CASE WHEN h.cur = 'USD' THEN h.val_usd ELSE 0 END) AS exec_usd,
                               SUM(CASE WHEN h.cur = 'EUR' THEN h.val_eur ELSE 0 END) AS exec_eur
                          FROM (SELECT s.product_id, s.currency_unit AS cur, c.val_krw, c.val_usd, c.val_eur
                                  FROM stock_curr c
                                  JOIN stock s ON s.s_id = c.s_id
                                 WHERE c.base_date = (SELECT MAX(base_date) FROM stock_curr)
                                   AND s.active_yn = 'Y'
                                UNION ALL
                                SELECT i.product_id, i.currency_unit, c.val_krw, c.val_usd, c.val_eur
                                  FROM investment_curr c
                                  JOIN investment i ON i.invest_id = c.invest_id
                                 WHERE c.base_date = (SELECT MAX(base_date) FROM investment_curr)
                                   AND i.active_yn = 'Y') h
                          JOIN product p                  ON p.product_id = h.product_id
                          JOIN product_type_mapping m     ON m.product_type_id = p.product_type_id
                          JOIN scheme_sub_asset_class ssc ON ssc.sub_class_id = m.sub_class_id
                                                         AND ssc.scheme_id = :schemeId
                          JOIN scheme_asset_class sac     ON sac.scheme_id = :schemeId
                                                         AND sac.scheme_ac_id = ssc.scheme_ac_id
                         GROUP BY sac.scheme_ac_id
                         ORDER BY sac.scheme_ac_id
                        """)
                .param("schemeId", PORTFOLIO_SCHEME_ID)
                .query((rs, rowNum) -> new ExecAmountDto(
                        rs.getInt("scheme_ac_id"),
                        rs.getLong("exec_krw"),
                        rs.getBigDecimal("exec_usd"),
                        rs.getBigDecimal("exec_eur")))
                .list();
    }
}
