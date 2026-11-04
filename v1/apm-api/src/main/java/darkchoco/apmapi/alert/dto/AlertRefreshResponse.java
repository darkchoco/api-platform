package darkchoco.apmapi.alert.dto;

/** 수동 새로고침(POST /api/alerts/refresh) 결과. 소스별로 독립이라 하나가 실패해도 나머지는 담긴다. */
public record AlertRefreshResponse(SourceRefreshResult maturity, SourceRefreshResult jira,
                                   SourceRefreshResult todoist) {
}
