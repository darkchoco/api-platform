package darkchoco.apmapi.portfolio.repository;

import static darkchoco.apmapi.portfolio.repository.TargetRepository.PORTFOLIO_SCHEME_ID;

import darkchoco.apmapi.portfolio.dto.PlanDetailDto;
import darkchoco.apmapi.portfolio.dto.VersionSaveRequest;
import darkchoco.apmapi.portfolio.dto.VersionSummaryResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class VersionRepository {

    private final JdbcClient jdbc;

    public VersionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<VersionSummaryResponse> findSummaries() {
        return jdbc.sql("""
                        SELECT version_id, version_label, COALESCE(upd_date, ins_date) AS updated_at
                          FROM portfolio_version
                         ORDER BY version_id DESC
                        """)
                .query((rs, rowNum) -> new VersionSummaryResponse(
                        rs.getInt("version_id"),
                        rs.getString("version_label"),
                        rs.getObject("updated_at", LocalDateTime.class)))
                .list();
    }

    public Optional<VersionHeader> findHeader(int versionId) {
        return jdbc.sql("SELECT * FROM portfolio_version WHERE version_id = :versionId")
                .param("versionId", versionId)
                .query((rs, rowNum) -> toHeader(rs))
                .optional();
    }

    /** "최신" 버전 = version_id 최대값. 버전이 하나도 없으면 빈 값. */
    public Optional<Integer> findLatestVersionId() {
        return jdbc.sql("SELECT MAX(version_id) FROM portfolio_version")
                .query((rs, rowNum) -> rs.getObject(1, Integer.class))
                .optional();
    }

    public List<PlanDetailDto> findPlanDetails(int versionId) {
        return jdbc.sql("""
                        SELECT scheme_ac_id, krw_usd_ratio, eur_ratio, exec_krw, exec_usd, exec_eur
                          FROM portfolio_plan_detail
                         WHERE version_id = :versionId AND scheme_id = :schemeId
                         ORDER BY scheme_ac_id
                        """)
                .param("versionId", versionId)
                .param("schemeId", PORTFOLIO_SCHEME_ID)
                .query((rs, rowNum) -> new PlanDetailDto(
                        rs.getInt("scheme_ac_id"),
                        rs.getBigDecimal("krw_usd_ratio"),
                        rs.getBigDecimal("eur_ratio"),
                        rs.getObject("exec_krw", Long.class),
                        rs.getBigDecimal("exec_usd"),
                        rs.getBigDecimal("exec_eur")))
                .list();
    }

    public boolean existsByLabel(String versionLabel) {
        Integer count = jdbc.sql("SELECT COUNT(*) FROM portfolio_version WHERE version_label = :label")
                .param("label", versionLabel)
                .query(Integer.class)
                .single();
        return count > 0;
    }

    /** 접두사(예: "v2610.")로 시작하는 라벨 전부. */
    public List<String> findLabelsByPrefix(String prefix) {
        return jdbc.sql("SELECT version_label FROM portfolio_version WHERE version_label LIKE :pattern")
                .param("pattern", prefix + "%")
                .query(String.class)
                .list();
    }

    /** 새 버전 헤더를 넣고 생성된 version_id를 돌려준다. UNIQUE(version_label) 위반 시 DuplicateKeyException. */
    public int insert(String versionLabel, VersionSaveRequest request, LocalDateTime now) {
        Map<String, Object> params = headerParams(request);
        params.put("label", versionLabel);
        params.put("now", now);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.sql("""
                        INSERT INTO portfolio_version
                               (version_label, avail_krw, avail_usd, avail_eur, plan_krw, plan_usd, plan_eur,
                                fx_usd, fx_eur, fetch_date, ins_date)
                        VALUES (:label, :availKrw, :availUsd, :availEur, :planKrw, :planUsd, :planEur,
                                :fxUsd, :fxEur, :fetchDate, :now)
                        """)
                .params(params)
                .update(keyHolder);

        return keyHolder.getKey().intValue();
    }

    /** 라벨은 그대로 두고 값만 갱신한다. 대상 버전이 있는지는 호출하는 쪽이 먼저 확인한다. */
    public void update(int versionId, VersionSaveRequest request, LocalDateTime now) {
        Map<String, Object> params = headerParams(request);
        params.put("versionId", versionId);
        params.put("now", now);

        jdbc.sql("""
                        UPDATE portfolio_version
                           SET avail_krw = :availKrw, avail_usd = :availUsd, avail_eur = :availEur,
                               plan_krw = :planKrw, plan_usd = :planUsd, plan_eur = :planEur,
                               fx_usd = :fxUsd, fx_eur = :fxEur, fetch_date = :fetchDate, upd_date = :now
                         WHERE version_id = :versionId
                        """)
                .params(params)
                .update();
    }

    /** 대상별 detail을 통째로 교체한다. 호출하는 쪽의 트랜잭션 안에서 실행해야 한다. */
    public void replacePlanDetails(int versionId, List<PlanDetailDto> detail) {
        jdbc.sql("DELETE FROM portfolio_plan_detail WHERE version_id = :versionId AND scheme_id = :schemeId")
                .param("versionId", versionId)
                .param("schemeId", PORTFOLIO_SCHEME_ID)
                .update();

        for (PlanDetailDto row : detail) {
            jdbc.sql("""
                            INSERT INTO portfolio_plan_detail
                                   (version_id, scheme_id, scheme_ac_id, krw_usd_ratio, eur_ratio,
                                    exec_krw, exec_usd, exec_eur)
                            VALUES (:versionId, :schemeId, :schemeAcId, :krwUsdRatio, :eurRatio,
                                    :execKrw, :execUsd, :execEur)
                            """)
                    .param("versionId", versionId)
                    .param("schemeId", PORTFOLIO_SCHEME_ID)
                    .param("schemeAcId", row.schemeAcId())
                    .param("krwUsdRatio", row.krwUsdRatio())
                    .param("eurRatio", row.eurRatio())
                    .param("execKrw", row.execKrw())
                    .param("execUsd", row.execUsd())
                    .param("execEur", row.execEur())
                    .update();
        }
    }

    // 값이 null이어도 그대로 바인딩해야 하므로 null을 허용하는 HashMap으로 만든다 (Map.of는 null 불가).
    private Map<String, Object> headerParams(VersionSaveRequest request) {
        Map<String, Object> params = new HashMap<>();
        params.put("availKrw", request.availKrw());
        params.put("availUsd", request.availUsd());
        params.put("availEur", request.availEur());
        params.put("planKrw", request.planKrw());
        params.put("planUsd", request.planUsd());
        params.put("planEur", request.planEur());
        params.put("fxUsd", request.fxUsd());
        params.put("fxEur", request.fxEur());
        params.put("fetchDate", request.fetchDate());
        return params;
    }

    private VersionHeader toHeader(ResultSet rs) throws SQLException {
        return new VersionHeader(
                rs.getInt("version_id"),
                rs.getString("version_label"),
                rs.getObject("avail_krw", Long.class),
                rs.getBigDecimal("avail_usd"),
                rs.getBigDecimal("avail_eur"),
                rs.getObject("plan_krw", Long.class),
                rs.getBigDecimal("plan_usd"),
                rs.getBigDecimal("plan_eur"),
                rs.getBigDecimal("fx_usd"),
                rs.getBigDecimal("fx_eur"),
                rs.getObject("fetch_date", LocalDateTime.class),
                rs.getObject("ins_date", LocalDateTime.class),
                rs.getObject("upd_date", LocalDateTime.class));
    }
}
