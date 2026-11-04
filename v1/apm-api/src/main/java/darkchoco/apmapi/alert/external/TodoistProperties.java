package darkchoco.apmapi.alert.external;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * Todoist 접속 정보. 토큰은 환경변수로만 받는다(application.yml의 ALERT_TODOIST_API_TOKEN 참고).
 *
 * @param apiToken Todoist 개인 API 토큰
 * @param label    가져올 태스크의 라벨
 * @param timeout  호출 하나의 연결·응답 타임아웃
 */
@ConfigurationProperties("app.alert.todoist")
public record TodoistProperties(
        String apiToken,
        @DefaultValue("apm_alert") String label,
        @DefaultValue("10s") Duration timeout) {

    public boolean isConfigured() {
        return StringUtils.hasText(apiToken);
    }
}
