package darkchoco.apmapi.portfolio.repository;

import static darkchoco.apmapi.portfolio.repository.HoldingQueries.LATEST_ACTIVE_HOLDINGS;
import static darkchoco.apmapi.portfolio.repository.HoldingQueries.TARGET_MAPPING_JOINS;
import static darkchoco.apmapi.portfolio.repository.TargetRepository.PORTFOLIO_SCHEME_ID;

import darkchoco.apmapi.portfolio.domain.HoldingCurrency;
import java.sql.Types;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 현재 보유 종목의 최신 기준월 시가 조회. */
@Repository
public class HoldingRepository {

    private final JdbcClient jdbc;

    public HoldingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 종목 × 통화 단위의 시가. 실행 금액 집계와 같은 규칙으로 해당 통화의 컬럼만 합산하고,
     * Portfolio가 다루지 않는 통화(JPY)와 대상에 매핑되지 않는 종목은 제외한다.
     * 정렬: schemeAcId, productNm, productId, 통화(한 종목에 통화가 둘 이상이면 알파벳순 첫 통화가 먼저 온다).
     *
     * @param schemeAcId 지정하면 그 대상의 종목만, null이면 전체
     */
    public List<HoldingRow> findHoldings(Integer schemeAcId) {
        return query(schemeAcId, null);
    }

    /** 한 종목의 현재 보유분. 보유 중이 아니거나 Portfolio 대상이 아니면 빈 목록. 통화별로 최대 한 행씩 나온다. */
    public List<HoldingRow> findHoldingsOfProduct(int productId) {
        return query(null, productId);
    }

    private List<HoldingRow> query(Integer schemeAcId, Integer productId) {
        return jdbc.sql("""
                        SELECT p.product_id, sac.scheme_ac_id, p.product_nm, p.symbol_alias, p.product_nm_detail, h.cur,
                               COALESCE(SUM(CASE WHEN h.cur = 'KRW' THEN h.val_krw ELSE 0 END), 0) AS current_krw,
                               COALESCE(SUM(CASE WHEN h.cur = 'USD' THEN h.val_usd ELSE 0 END), 0) AS current_usd,
                               COALESCE(SUM(CASE WHEN h.cur = 'EUR' THEN h.val_eur ELSE 0 END), 0) AS current_eur
                          FROM\s""" + LATEST_ACTIVE_HOLDINGS + TARGET_MAPPING_JOINS + """
                         WHERE h.cur IN ('KRW', 'USD', 'EUR')
                           AND (:schemeAcId IS NULL OR sac.scheme_ac_id = :schemeAcId)
                           AND (:productId IS NULL OR p.product_id = :productId)
                         GROUP BY p.product_id, sac.scheme_ac_id, p.product_nm, p.symbol_alias, p.product_nm_detail, h.cur
                         ORDER BY sac.scheme_ac_id, p.product_nm, p.product_id, h.cur
                        """)
                .param("schemeId", PORTFOLIO_SCHEME_ID)
                .param("schemeAcId", schemeAcId, Types.INTEGER)
                .param("productId", productId, Types.INTEGER)
                .query((rs, rowNum) -> new HoldingRow(
                        rs.getInt("product_id"),
                        rs.getInt("scheme_ac_id"),
                        rs.getString("product_nm"),
                        rs.getString("symbol_alias"),
                        rs.getString("product_nm_detail"),
                        HoldingCurrency.valueOf(rs.getString("cur")),
                        rs.getLong("current_krw"),
                        rs.getBigDecimal("current_usd"),
                        rs.getBigDecimal("current_eur")))
                .list();
    }
}
