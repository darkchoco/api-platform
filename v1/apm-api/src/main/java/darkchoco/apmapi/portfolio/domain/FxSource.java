package darkchoco.apmapi.portfolio.domain;

/** 기준환율의 출처. */
public enum FxSource {

    /** ex_rate 테이블에서 기준일 전후 3일 이내 최근접 값을 조회했다. */
    EX_RATE,

    /** 사용자가 직접 입력한 값(쿼리 파라미터)을 그대로 썼다. */
    MANUAL
}
