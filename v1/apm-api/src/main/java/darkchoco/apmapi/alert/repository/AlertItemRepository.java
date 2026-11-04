package darkchoco.apmapi.alert.repository;

import darkchoco.apmapi.alert.domain.AlertSourceType;
import darkchoco.apmapi.alert.domain.AlertTier;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 알림 항목(alert_item). 한 (source_type, source_key, tier)에 행 하나다. */
@Repository
public class AlertItemRepository {

    private static final int TITLE_MAX_LENGTH = 255;

    private final JdbcClient jdbc;

    public AlertItemRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 알림을 OPEN으로 넣거나 갱신한다. 새 행은 dismissed_yn='N'이고, 이미 있는 행은 dismissed_yn을 그대로 둔다
     * (사용자가 닫은 알림을 서버가 다시 띄우지 않는다).
     */
    public void upsertOpen(AlertSourceType sourceType, String sourceKey, AlertTier tier, String title,
                           String detailUrl, LocalDateTime dueDate, LocalDateTime now) {
        // MySQL 8.0.20+에서 deprecated된 VALUES() 대신 같은 값을 이름 있는 파라미터로 다시 바인딩한다.
        jdbc.sql("""
                        INSERT INTO alert_item (source_type, source_key, tier, status, title, detail_url, due_date,
                                                dismissed_yn, upd_date)
                        VALUES (:sourceType, :sourceKey, :tier, 'OPEN', :title, :detailUrl, :dueDate, 'N', :now)
                        ON DUPLICATE KEY UPDATE status = 'OPEN', title = :title, detail_url = :detailUrl,
                                                due_date = :dueDate, upd_date = :now
                        """)
                .param("sourceType", sourceType.name())
                .param("sourceKey", sourceKey)
                .param("tier", tier.name())
                .param("title", truncateTitle(title))
                .param("detailUrl", detailUrl)
                .param("dueDate", dueDate)
                .param("now", now)
                .update();
    }

    /** 소스의 OPEN 행 전부 (닫힘 여부와 무관). */
    public List<OpenAlert> findOpen(AlertSourceType sourceType) {
        return jdbc.sql("""
                        SELECT alert_id, source_key, tier
                        FROM alert_item
                        WHERE source_type = :sourceType
                          AND status = 'OPEN'
                        """)
                .param("sourceType", sourceType.name())
                .query((rs, rowNum) -> new OpenAlert(
                        rs.getInt("alert_id"),
                        rs.getString("source_key"),
                        AlertTier.valueOf(rs.getString("tier"))))
                .list();
    }

    /**
     * 조건에서 벗어난 알림을 CLOSE로 바꾼다. dismissed_yn은 건드리지 않는다(자연 소멸).
     *
     * @return CLOSE로 바꾼 행 수
     */
    public int close(List<Integer> alertIds, LocalDateTime now) {
        if (alertIds.isEmpty()) {
            return 0;
        }
        return jdbc.sql("UPDATE alert_item SET status = 'CLOSE', upd_date = :now WHERE alert_id IN (:alertIds)")
                .param("now", now)
                .param("alertIds", alertIds)
                .update();
    }

    /** 화면에 표시할 알림 전부: status='OPEN' AND dismissed_yn='N'. 정렬은 서비스가 한다. */
    public List<VisibleAlert> findVisible() {
        return jdbc.sql("""
                        SELECT alert_id, source_type, tier, title, detail_url, due_date
                        FROM alert_item
                        WHERE status = 'OPEN'
                          AND dismissed_yn = 'N'
                        """)
                .query((rs, rowNum) -> new VisibleAlert(
                        rs.getInt("alert_id"),
                        AlertSourceType.valueOf(rs.getString("source_type")),
                        AlertTier.valueOf(rs.getString("tier")),
                        rs.getString("title"),
                        rs.getString("detail_url"),
                        rs.getObject("due_date", LocalDateTime.class)))
                .list();
    }

    public boolean exists(int alertId) {
        return jdbc.sql("SELECT COUNT(*) FROM alert_item WHERE alert_id = :alertId")
                .param("alertId", alertId)
                .query(Integer.class)
                .single() > 0;
    }

    /**
     * 알림 하나를 닫는다. 이미 닫혀 있으면 아무것도 바꾸지 않는다(닫은 시각을 처음 값으로 유지).
     *
     * @return 이번에 닫은 행 수(0 또는 1)
     */
    public int dismiss(int alertId, LocalDateTime now) {
        return jdbc.sql("""
                        UPDATE alert_item
                        SET dismissed_yn = 'Y', dismissed_at = :now
                        WHERE alert_id = :alertId
                          AND dismissed_yn = 'N'
                        """)
                .param("now", now)
                .param("alertId", alertId)
                .update();
    }

    /**
     * 지금 화면에 표시되는 알림(findVisible과 같은 조건)을 모두 닫는다.
     *
     * @return 닫은 행 수
     */
    public int dismissAllVisible(LocalDateTime now) {
        return jdbc.sql("""
                        UPDATE alert_item
                        SET dismissed_yn = 'Y', dismissed_at = :now
                        WHERE status = 'OPEN'
                          AND dismissed_yn = 'N'
                        """)
                .param("now", now)
                .update();
    }

    // title은 VARCHAR(255)다. 상품명(255)에 금융기관명이 붙으면 넘칠 수 있어 잘라서 저장한다.
    private static String truncateTitle(String title) {
        return title.length() <= TITLE_MAX_LENGTH ? title : title.substring(0, TITLE_MAX_LENGTH);
    }
}
