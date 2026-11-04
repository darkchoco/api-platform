package darkchoco.apmapi.alert.domain;

/**
 * 소스 하나를 재계산(재조회)한 결과.
 *
 * @param open   이번 실행에서 OPEN으로 upsert한 행 수
 * @param closed 이번 실행에서 CLOSE로 바꾼 행 수
 */
public record RefreshCount(int open, int closed) {
}
