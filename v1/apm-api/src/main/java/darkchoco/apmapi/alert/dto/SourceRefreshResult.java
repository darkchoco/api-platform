package darkchoco.apmapi.alert.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import darkchoco.apmapi.alert.domain.RefreshCount;
import darkchoco.apmapi.alert.domain.SourceRefreshStatus;

/**
 * 새로고침에서 소스 하나의 결과. OK면 open/closed만, FAILED/SKIPPED면 reason만 내려간다(null 필드는 생략).
 *
 * @param open   이번 실행에서 OPEN으로 upsert한 행 수
 * @param closed 이번 실행에서 CLOSE로 바꾼 행 수
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SourceRefreshResult(SourceRefreshStatus status, Integer open, Integer closed, String reason) {

    public static SourceRefreshResult ok(RefreshCount count) {
        return new SourceRefreshResult(SourceRefreshStatus.OK, count.open(), count.closed(), null);
    }

    public static SourceRefreshResult failed(String reason) {
        return new SourceRefreshResult(SourceRefreshStatus.FAILED, null, null, reason);
    }

    public static SourceRefreshResult skipped(String reason) {
        return new SourceRefreshResult(SourceRefreshStatus.SKIPPED, null, null, reason);
    }
}
