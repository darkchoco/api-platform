package darkchoco.apmapi.portfolio.dto;

/**
 * 저장 다이얼로그에 미리 보여줄 라벨. 표시용이며 실제 저장 시 서버가 같은 규칙으로 다시 계산한다.
 *
 * @param fromBase baseVersionId를 주지 않았으면 null
 */
public record NextLabelsResponse(ThisMonth thisMonth, FromBase fromBase) {

    /** 이번 달 {@code v{YYMM}.0}. 이미 존재하면 available=false. */
    public record ThisMonth(String versionLabel, boolean available) {
    }

    /** 기준 버전에서 {@code .n}을 올린 라벨. */
    public record FromBase(String versionLabel, String baseVersionLabel) {
    }
}
