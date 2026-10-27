package darkchoco.apmapi.portfolio.repository;

/**
 * 보유 건을 Portfolio 대상에 매핑하는 공통 SQL 조각. 실행 금액 집계(PORT-BE-2)와 종목별 현황(PORT-BE-3)이
 * 같은 규칙을 쓰도록 한 곳에 둔다. 두 조각을 {@code FROM} 뒤에 이어 붙여 쓰며, {@code :schemeId} 파라미터가 필요하다.
 */
final class HoldingQueries {

    private HoldingQueries() {
    }

    /**
     * 각 보유 테이블의 최신 기준월(MAX(base_date)) 중 현재 보유(active_yn='Y') 건.
     * 별칭 {@code h}: product_id, cur(보유 통화), val_krw, val_usd, val_eur.
     * val_krw는 USD·EUR 보유 건에도 원화 환산액이 채워져 있으므로, 합산할 때는 반드시 cur에 해당하는 컬럼만 쓴다.
     */
    static final String LATEST_ACTIVE_HOLDINGS = """
            (SELECT s.product_id, s.currency_unit AS cur, c.val_krw, c.val_usd, c.val_eur
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
            """;

    /**
     * 보유 건 → 상품 → 상품유형 → 세부분류 → 대상. 별칭 {@code p}(product), {@code sac}(scheme_asset_class).
     * 대상에 매핑되지 않는 보유 건(연금, 보험 등)은 inner join으로 의도적으로 제외된다.
     */
    static final String TARGET_MAPPING_JOINS = """
            JOIN product p                  ON p.product_id = h.product_id
            JOIN product_type_mapping m     ON m.product_type_id = p.product_type_id
            JOIN scheme_sub_asset_class ssc ON ssc.sub_class_id = m.sub_class_id
                                           AND ssc.scheme_id = :schemeId
            JOIN scheme_asset_class sac     ON sac.scheme_id = :schemeId
                                           AND sac.scheme_ac_id = ssc.scheme_ac_id
            """;
}
