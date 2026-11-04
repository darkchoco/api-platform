package darkchoco.apmapi.alert.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import darkchoco.apmapi.alert.domain.AlertSourceType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Jira Cloud에서 apm_alert 라벨이 붙은 미완료 이슈를 가져온다.
 * 구 검색 API(/rest/api/3/search)는 폐지되어 JQL 검색 API(POST /rest/api/3/search/jql)를 쓴다.
 * 페이지는 nextPageToken으로 넘기고, JQL은 URL 인코딩 문제가 없도록 요청 본문에 담는다.
 */
@Component
public class JiraAlertSource implements ExternalAlertSource {

    private static final String SEARCH_PATH = "/rest/api/3/search/jql";
    private static final List<String> FIELDS = List.of("summary", "duedate");
    private static final int PAGE_SIZE = 100;
    // nextPageToken이 끝나지 않는 경우를 막는 상한. 개인 용도에서 5,000건을 넘을 일은 없다.
    private static final int MAX_PAGES = 50;

    private final JiraProperties properties;
    private final RestClient restClient;

    @Autowired
    public JiraAlertSource(JiraProperties properties) {
        this(properties, RestClient.builder().requestFactory(ExternalHttpClients.requestFactory(properties.timeout())));
    }

    /** 테스트에서 가짜 서버에 묶인 builder를 넘길 때 쓴다. */
    JiraAlertSource(JiraProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = properties.isConfigured()
                ? builder.baseUrl(baseUrl())
                        .defaultHeaders(headers -> headers.setBasicAuth(properties.email(), properties.apiToken()))
                        .build()
                : null;
    }

    @Override
    public AlertSourceType sourceType() {
        return AlertSourceType.JIRA;
    }

    @Override
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    @Override
    public List<ExternalAlert> fetchOpenAlerts() {
        String jql = "labels = \"%s\" AND statusCategory != Done ORDER BY key".formatted(properties.label());

        List<ExternalAlert> alerts = new ArrayList<>();
        String pageToken = null;
        for (int page = 0; page < MAX_PAGES; page++) {
            SearchResponse response = restClient.post()
                    .uri(SEARCH_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SearchRequest(jql, FIELDS, PAGE_SIZE, pageToken))
                    .retrieve()
                    .body(SearchResponse.class);
            Objects.requireNonNull(response, "Jira 검색 응답이 비어 있습니다");

            response.issues().forEach(issue -> alerts.add(toAlert(issue)));

            String nextToken = response.nextPageToken();
            if (nextToken == null || nextToken.isBlank() || nextToken.equals(pageToken)) {
                return alerts;
            }
            pageToken = nextToken;
        }
        throw new IllegalStateException("Jira 검색 결과가 %d페이지를 넘습니다".formatted(MAX_PAGES));
    }

    private ExternalAlert toAlert(Issue issue) {
        return new ExternalAlert(
                issue.key(),
                issue.key() + " " + issue.fields().summary(),
                baseUrl() + "/browse/" + issue.key(),
                issue.fields().duedate());
    }

    // 끝에 붙은 '/'는 떼어 "{baseUrl}/browse/{key}"가 '//'가 되지 않게 한다.
    private String baseUrl() {
        return properties.baseUrl().replaceAll("/+$", "");
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SearchRequest(String jql, List<String> fields, int maxResults, String nextPageToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchResponse(List<Issue> issues, String nextPageToken) {

        SearchResponse {
            issues = issues == null ? List.of() : issues;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Issue(String key, Fields fields) {
    }

    /** @param duedate Jira 기한(yyyy-MM-dd). 없으면 null */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Fields(String summary, LocalDate duedate) {
    }
}
