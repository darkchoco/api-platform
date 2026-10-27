package darkchoco.apmapi.portfolio.repository;

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
}
