package darkchoco.apmapi.alert.external;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * Jira Cloud 접속 정보. 비밀값은 환경변수로만 받는다(application.yml의 ALERT_JIRA_* 참고).
 *
 * @param baseUrl  예: https://xxx.atlassian.net
 * @param email    API 토큰을 발급한 계정의 이메일(Basic Auth 사용자명)
 * @param apiToken Jira API 토큰
 * @param label    가져올 이슈의 라벨
 * @param timeout  호출 하나의 연결·응답 타임아웃
 */
@ConfigurationProperties("app.alert.jira")
public record JiraProperties(
        String baseUrl,
        String email,
        String apiToken,
        @DefaultValue("apm_alert") String label,
        @DefaultValue("10s") Duration timeout) {

    public boolean isConfigured() {
        return StringUtils.hasText(baseUrl) && StringUtils.hasText(email) && StringUtils.hasText(apiToken);
    }
}
