package darkchoco.apmapi.portfolio.repository;

import darkchoco.apmapi.portfolio.domain.HoldingCurrency;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 종목별 투자계획금액(portfolio_item_plan). 버전 이력 없이 종목당 최신값 한 행만 유지한다. */
@Repository
public class ItemPlanRepository {

    private final JdbcClient jdbc;

    public ItemPlanRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ItemPlan> findAll() {
        return jdbc.sql("SELECT product_id, plan_krw, plan_usd, plan_eur FROM portfolio_item_plan")
                .query((rs, rowNum) -> new ItemPlan(
                        rs.getInt("product_id"),
                        rs.getObject("plan_krw", Long.class),
                        rs.getBigDecimal("plan_usd"),
                        rs.getBigDecimal("plan_eur")))
                .list();
    }

    /**
     * 종목의 통화 컬럼에만 금액을 넣고 나머지 두 컬럼은 NULL로 만든다 (이미 행이 있어도 덮어쓴다).
     * 금액은 이미 검증되어 통화의 자릿수(원화 정수, 달러·유로 소수 2자리)에 맞다고 가정한다.
     */
    public void upsert(int productId, HoldingCurrency currency, BigDecimal amount, LocalDateTime now) {
        BigDecimal planKrw = currency == HoldingCurrency.KRW ? amount.setScale(0, RoundingMode.UNNECESSARY) : null;
        BigDecimal planUsd = currency == HoldingCurrency.USD ? amount.setScale(2, RoundingMode.UNNECESSARY) : null;
        BigDecimal planEur = currency == HoldingCurrency.EUR ? amount.setScale(2, RoundingMode.UNNECESSARY) : null;

        // MySQL 8.0.20+에서 deprecated된 VALUES() 대신 같은 값을 이름 있는 파라미터로 다시 바인딩한다.
        jdbc.sql("""
                        INSERT INTO portfolio_item_plan (product_id, plan_krw, plan_usd, plan_eur, upd_date)
                        VALUES (:productId, :planKrw, :planUsd, :planEur, :now)
                        ON DUPLICATE KEY UPDATE plan_krw = :planKrw, plan_usd = :planUsd, plan_eur = :planEur,
                                                upd_date = :now
                        """)
                .param("productId", productId)
                .param("planKrw", planKrw)
                .param("planUsd", planUsd)
                .param("planEur", planEur)
                .param("now", now)
                .update();
    }

    /** "계획 없음"으로 되돌린다. 행이 없어도 오류가 아니다. */
    public void delete(int productId) {
        jdbc.sql("DELETE FROM portfolio_item_plan WHERE product_id = :productId")
                .param("productId", productId)
                .update();
    }
}
