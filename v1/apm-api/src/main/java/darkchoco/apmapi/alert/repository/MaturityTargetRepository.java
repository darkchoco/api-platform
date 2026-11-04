package darkchoco.apmapi.alert.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 만기 알림 대상(investment) 조회. 읽기 전용이다. */
@Repository
public class MaturityTargetRepository {

    private final JdbcClient jdbc;

    public MaturityTargetRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 보유 중이고 만기일이 있는 투자건 전부. 보유 여부는 active_yn만 본다(close_date는 보지 않는다 - 결정 B).
     * fin_inst는 이름이 없어도 대상에서 빠지지 않도록 LEFT JOIN한다.
     */
    public List<MaturityTarget> findActiveWithDueDate() {
        return jdbc.sql("""
                        SELECT i.invest_id, p.product_nm, f.fin_inst_nm, i.last_due_date
                        FROM investment i
                                 JOIN product p ON p.product_id = i.product_id
                                 JOIN acct a ON a.acct_id = i.acct_id
                                 LEFT JOIN fin_inst f ON f.fin_inst_id = a.fin_inst_id
                        WHERE i.active_yn = 'Y'
                          AND i.last_due_date IS NOT NULL
                        ORDER BY i.invest_id
                        """)
                .query((rs, rowNum) -> new MaturityTarget(
                        rs.getInt("invest_id"),
                        rs.getString("product_nm"),
                        rs.getString("fin_inst_nm"),
                        rs.getObject("last_due_date", LocalDateTime.class)))
                .list();
    }
}
