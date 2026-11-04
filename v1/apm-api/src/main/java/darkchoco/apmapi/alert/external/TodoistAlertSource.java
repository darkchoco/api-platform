package darkchoco.apmapi.alert.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import darkchoco.apmapi.alert.domain.AlertSourceType;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/**
 * Todoist에서 apm_alert 라벨이 붙은 미완료 태스크를 가져온다. Todoist API v1의 GET /tasks는 미완료 태스크만
 * 돌려주고 label로 거를 수 있다. 페이지는 cursor / next_cursor로 넘긴다.
 */
@Component
public class TodoistAlertSource implements ExternalAlertSource {

    private static final String BASE_URL = "https://api.todoist.com/api/v1";
    private static final String TASK_URL = "https://app.todoist.com/app/task/";
    private static final int PAGE_SIZE = 200;
    // next_cursor가 끝나지 않는 경우를 막는 상한. 개인 용도에서 10,000건을 넘을 일은 없다.
    private static final int MAX_PAGES = 50;

    private final TodoistProperties properties;
    private final RestClient restClient;

    @Autowired
    public TodoistAlertSource(TodoistProperties properties) {
        this(properties, RestClient.builder().requestFactory(ExternalHttpClients.requestFactory(properties.timeout())));
    }

    /** 테스트에서 가짜 서버에 묶인 builder를 넘길 때 쓴다. */
    TodoistAlertSource(TodoistProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = properties.isConfigured()
                ? builder.baseUrl(BASE_URL)
                        .defaultHeaders(headers -> headers.setBearerAuth(properties.apiToken()))
                        .build()
                : null;
    }

    @Override
    public AlertSourceType sourceType() {
        return AlertSourceType.TODOIST;
    }

    @Override
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    @Override
    public List<ExternalAlert> fetchOpenAlerts() {
        List<ExternalAlert> alerts = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < MAX_PAGES; page++) {
            TaskPage response = restClient.get()
                    .uri(tasksUri(cursor))
                    .retrieve()
                    .body(TaskPage.class);
            Objects.requireNonNull(response, "Todoist 응답이 비어 있습니다");

            response.results().forEach(task -> alerts.add(toAlert(task)));

            String nextCursor = response.nextCursor();
            if (nextCursor == null || nextCursor.isBlank() || nextCursor.equals(cursor)) {
                return alerts;
            }
            cursor = nextCursor;
        }
        throw new IllegalStateException("Todoist 태스크가 %d페이지를 넘습니다".formatted(MAX_PAGES));
    }

    // 라벨과 cursor는 URI 변수로 넘겨 특수문자까지 인코딩되게 한다.
    private Function<UriBuilder, URI> tasksUri(String cursor) {
        return uriBuilder -> {
            Map<String, Object> variables = new HashMap<>();
            variables.put("label", properties.label());
            uriBuilder.path("/tasks")
                    .queryParam("label", "{label}")
                    .queryParam("limit", PAGE_SIZE);
            if (cursor != null) {
                variables.put("cursor", cursor);
                uriBuilder.queryParam("cursor", "{cursor}");
            }
            return uriBuilder.build(variables);
        };
    }

    private static ExternalAlert toAlert(Task task) {
        return new ExternalAlert(task.id(), task.content(), TASK_URL + task.id(), dueDateOf(task));
    }

    // due.date는 "2026-11-05", "2026-11-05T10:00:00", "2026-11-05T09:00:00Z" 중 하나다. 알림은 날짜만 쓴다.
    private static LocalDate dueDateOf(Task task) {
        if (task.due() == null || task.due().date() == null) {
            return null;
        }
        return LocalDate.parse(task.due().date().substring(0, 10));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TaskPage(List<Task> results, @JsonProperty("next_cursor") String nextCursor) {

        TaskPage {
            results = results == null ? List.of() : results;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Task(String id, String content, Due due) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Due(String date) {
    }
}
