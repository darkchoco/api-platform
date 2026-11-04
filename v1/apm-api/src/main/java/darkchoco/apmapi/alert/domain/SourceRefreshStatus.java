package darkchoco.apmapi.alert.domain;

/** 새로고침(POST /api/alerts/refresh)에서 소스 하나의 실행 결과. */
public enum SourceRefreshStatus {
    /** 재계산(재조회)에 성공했다. */
    OK,
    /** 실패했다. 그 소스의 기존 알림은 그대로 둔다. */
    FAILED,
    /** 설정이 없어 실행하지 않았다. */
    SKIPPED
}
