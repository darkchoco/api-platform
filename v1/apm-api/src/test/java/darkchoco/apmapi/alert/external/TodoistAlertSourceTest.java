package darkchoco.apmapi.alert.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;

/** Todoist 태스크 조회와 매핑을 가짜 HTTP 서버로 검증한다(실제 Todoist는 부르지 않는다). */
class TodoistAlertSourceTest {

    private static final String TASKS_URL = "https://api.todoist.com/api/v1/tasks?label=apm_alert&limit=200";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    @Test
    void fetchOpenAlerts_followsCursorAndMapsTasks() {
        server.expect(requestTo(TASKS_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer secret"))
                .andRespond(withSuccess("""
                        { "results": [
                            { "id": "6X7rM8997g3RQmvh", "content": "보험 갱신 서류 제출",
                              "labels": ["apm_alert"], "due": { "date": "2026-11-20", "is_recurring": false } },
                            { "id": "6X7rfFVPjhvv84XG", "content": "시간 지정 태스크",
                              "due": { "date": "2026-11-21T09:00:00Z", "timezone": "Europe/Berlin" } } ],
                          "next_cursor": "c+2/=" }
                        """, MediaType.APPLICATION_JSON));
        // cursor 값의 특수문자(+, /, =)도 인코딩해서 보낸다
        server.expect(requestTo(TASKS_URL + "&cursor=c%2B2%2F%3D"))
                .andRespond(withSuccess("""
                        { "results": [ { "id": "6X7rfEVP8hvv25ZQ", "content": "기한 없음", "due": null } ],
                          "next_cursor": null }
                        """, MediaType.APPLICATION_JSON));

        List<ExternalAlert> alerts = source("secret").fetchOpenAlerts();

        assertThat(alerts).containsExactly(
                new ExternalAlert("6X7rM8997g3RQmvh", "보험 갱신 서류 제출",
                        "https://app.todoist.com/app/task/6X7rM8997g3RQmvh", LocalDate.of(2026, 11, 20)),
                new ExternalAlert("6X7rfFVPjhvv84XG", "시간 지정 태스크",
                        "https://app.todoist.com/app/task/6X7rfFVPjhvv84XG", LocalDate.of(2026, 11, 21)),
                new ExternalAlert("6X7rfEVP8hvv25ZQ", "기한 없음",
                        "https://app.todoist.com/app/task/6X7rfEVP8hvv25ZQ", null));
        server.verify();
    }

    @Test
    void fetchOpenAlerts_throwsOnUnauthorized() {
        server.expect(requestTo(TASKS_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> source("wrong").fetchOpenAlerts()).isInstanceOf(RestClientException.class);
    }

    @Test
    void isConfigured_requiresToken() {
        assertThat(source("secret").isConfigured()).isTrue();
        assertThat(source("").isConfigured()).isFalse();
        assertThat(source(null).isConfigured()).isFalse();
    }

    private TodoistAlertSource source(String apiToken) {
        return new TodoistAlertSource(new TodoistProperties(apiToken, "apm_alert", Duration.ofSeconds(10)), builder);
    }
}
