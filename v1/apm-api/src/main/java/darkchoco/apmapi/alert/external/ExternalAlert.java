package darkchoco.apmapi.alert.external;

import java.time.LocalDate;

/**
 * 외부 소스(Jira, Todoist)에서 가져온 알림 한 건. alert_item에는 tier='DEFAULT'로 저장한다.
 *
 * @param sourceKey Jira 이슈 키 / Todoist task id
 * @param dueDate   마감일. 없으면 null
 */
public record ExternalAlert(String sourceKey, String title, String detailUrl, LocalDate dueDate) {
}
