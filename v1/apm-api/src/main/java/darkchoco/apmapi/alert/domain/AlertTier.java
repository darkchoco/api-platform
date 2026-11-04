package darkchoco.apmapi.alert.domain;

import java.util.Optional;

/**
 * 알림의 단계. 선언 순서가 곧 심각도 순서다(목록 정렬에 쓴다).
 * MATURITY만 OVERDUE/URGENT/MILD를 쓰고, Jira/Todoist는 DEFAULT 하나다.
 */
public enum AlertTier {
    OVERDUE,
    URGENT,
    MILD,
    DEFAULT;

    private static final int URGENT_DAYS = 14;
    private static final int MILD_DAYS = 30;

    /**
     * 만기일까지 남은 일수로 만기 알림 단계를 정한다.
     *
     * @param daysLeft 만기일 − 오늘 (음수 = 만기 경과)
     * @return 30일보다 많이 남았으면 알림 없음(empty)
     */
    public static Optional<AlertTier> forMaturity(long daysLeft) {
        if (daysLeft < 0) {
            return Optional.of(OVERDUE);
        }
        if (daysLeft <= URGENT_DAYS) {
            return Optional.of(URGENT);
        }
        if (daysLeft <= MILD_DAYS) {
            return Optional.of(MILD);
        }
        return Optional.empty();
    }
}
