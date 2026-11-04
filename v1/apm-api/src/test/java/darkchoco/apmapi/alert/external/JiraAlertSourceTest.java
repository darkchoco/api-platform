package darkchoco.apmapi.alert.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Jira 검색 호출과 매핑을 가짜 HTTP 서버로 검증한다(실제 Jira는 부르지 않는다). */
class JiraAlertSourceTest {

    private static final String SEARCH_URL = "https://jira.example.test/rest/api/3/search/jql";
    private static final String JQL = "labels = \"apm_alert\" AND statusCategory != Done ORDER BY key";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    @Test
    void fetchOpenAlerts_followsNextPageTokenAndMapsIssues() {
        server.expect(requestTo(SEARCH_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", basicAuth("me@example.test", "secret")))
                .andExpect(jsonPath("$.jql").value(JQL))
                .andExpect(jsonPath("$.fields.length()").value(2))
                .andExpect(jsonPath("$.fields[0]").value("summary"))
                .andExpect(jsonPath("$.fields[1]").value("duedate"))
                .andExpect(jsonPath("$.maxResults").value(100))
                .andExpect(jsonPath("$.nextPageToken").doesNotExist())
                .andRespond(withSuccess("""
                        { "issues": [ { "id": "1", "key": "APM-1",
                                        "fields": { "summary": "환율 알림 확인", "duedate": "2026-11-20" } } ],
                          "nextPageToken": "page-2", "isLast": false }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(SEARCH_URL))
                .andExpect(jsonPath("$.nextPageToken").value("page-2"))
                .andRespond(withSuccess("""
                        { "issues": [ { "id": "2", "key": "APM-2",
                                        "fields": { "summary": "기한 없음", "duedate": null } } ],
                          "isLast": true }
                        """, MediaType.APPLICATION_JSON));

        List<ExternalAlert> alerts = source("https://jira.example.test/").fetchOpenAlerts();

        assertThat(alerts).containsExactly(
                new ExternalAlert("APM-1", "APM-1 환율 알림 확인", "https://jira.example.test/browse/APM-1",
                        LocalDate.of(2026, 11, 20)),
                new ExternalAlert("APM-2", "APM-2 기한 없음", "https://jira.example.test/browse/APM-2", null));
        server.verify();
    }

    @Test
    void fetchOpenAlerts_throwsOnServerErrorSoThatExistingAlertsAreKept() {
        server.expect(requestTo(SEARCH_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> source("https://jira.example.test").fetchOpenAlerts())
                .isInstanceOf(RestClientException.class);
    }

    @Test
    void isConfigured_requiresBaseUrlEmailAndToken() {
        assertThat(source("https://jira.example.test").isConfigured()).isTrue();
        assertThat(new JiraAlertSource(new JiraProperties("https://jira.example.test", "me@example.test", "",
                "apm_alert", Duration.ofSeconds(10)), builder).isConfigured()).isFalse();
        assertThat(new JiraAlertSource(new JiraProperties(null, null, null,
                "apm_alert", Duration.ofSeconds(10)), builder).isConfigured()).isFalse();
    }

    private JiraAlertSource source(String baseUrl) {
        JiraProperties properties = new JiraProperties(baseUrl, "me@example.test", "secret", "apm_alert",
                Duration.ofSeconds(10));
        return new JiraAlertSource(properties, builder);
    }

    private static String basicAuth(String user, String password) {
        String credentials = user + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
