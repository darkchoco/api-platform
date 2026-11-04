package darkchoco.apmapi.alert.domain;

/** 알림이 어디서 왔는지. alert_item.source_type에 이름 그대로 저장한다. */
public enum AlertSourceType {
    MATURITY,
    JIRA,
    TODOIST
}
