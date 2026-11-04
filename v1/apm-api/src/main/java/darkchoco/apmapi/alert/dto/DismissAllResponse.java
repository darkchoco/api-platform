package darkchoco.apmapi.alert.dto;

/** 전체 닫기(POST /api/alerts/dismiss-all) 결과. */
public record DismissAllResponse(int dismissedCount) {
}
