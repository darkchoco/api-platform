package darkchoco.apmapi.portfolio.repository;

import darkchoco.apmapi.portfolio.dto.TargetResponse;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TargetRepository {

    /** Portfolio 대상을 정의하는 scheme_asset_class의 scheme_id. */
    public static final int PORTFOLIO_SCHEME_ID = 2;

    private final JdbcClient jdbc;

    public TargetRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<TargetResponse> findAll() {
        return jdbc.sql("""
                        SELECT scheme_ac_id, name
                          FROM scheme_asset_class
                         WHERE scheme_id = :schemeId
                         ORDER BY scheme_ac_id
                        """)
                .param("schemeId", PORTFOLIO_SCHEME_ID)
                .query((rs, rowNum) -> new TargetResponse(rs.getInt("scheme_ac_id"), rs.getString("name")))
                .list();
    }
}
