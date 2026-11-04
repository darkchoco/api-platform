# ALERT-BE-2 — Jira / Todoist 알림 연동

- 상태: **코드·테스트 완료, 실제 연동 검증은 대기**(결정 E: 접속 정보와 `apm_alert` 테스트 항목 준비 후). 커밋은 `git log`의 `ALERT-BE-2` 항목 참고
- 요구사항: [API-REQUIREMENTS-v3-alert.md](../API-REQUIREMENTS-v3-alert.md) 4장, 5.4, 결정 D·E
- 이전: [ALERT-BE-3](ALERT-BE-3-api.md)

## 1. 구현

### 흐름 (`ExternalAlertSyncService.sync(source)`, 소스마다 독립)
1. 설정이 비어 있으면 **건너뜀** → `SKIPPED` / `not configured` (INFO 로그)
2. 외부 소스 호출. 실패하면 **그 소스의 기존 `alert_item`은 그대로** → `FAILED` / `fetch failed` (WARN 로그, 토큰 미기록)
3. 성공했을 때만 **한 트랜잭션**으로 반영: 가져온 항목은 `(source_type, source_key, 'DEFAULT')`로 OPEN upsert(`dismissed_yn` 유지), 목록에 없는 열린 항목은 `CLOSE` → `OK` / `open`, `closed`
- `sync`는 예외를 던지지 않는다. 한 소스의 실패가 다른 소스·기동·새로고침 응답에 영향을 주지 않는다.
- 실행 시점: **기동 시 1회**(`AlertStartupRefresher`, 만기 계산 다음에 Jira → Todoist 순서) + **`POST /api/alerts/refresh`**. 별도 주기 스케줄은 없다.

### Jira (`JiraAlertSource`)
- `POST {baseUrl}/rest/api/3/search/jql` — 구 `/rest/api/3/search`는 폐지되어 JQL 검색 API를 쓴다. 본문: `{"jql": …, "fields": ["summary","duedate"], "maxResults": 100, "nextPageToken": …}`
- JQL: `labels = "apm_alert" AND statusCategory != Done ORDER BY key`
- Basic Auth(`email:apiToken`). 페이지는 응답의 `nextPageToken`이 없을 때까지 넘긴다.
- 매핑: `source_key=이슈키`, `title="{key} {summary}"`, `detail_url="{baseUrl}/browse/{key}"`, `due_date=duedate`(없으면 NULL)

### Todoist (`TodoistAlertSource`)
- `GET https://api.todoist.com/api/v1/tasks?label=apm_alert&limit=200[&cursor=…]` — v1의 `/tasks`는 미완료 태스크만 돌려준다. `Authorization: Bearer {token}`. 페이지는 `next_cursor`가 null일 때까지 넘긴다.
- 매핑: `source_key=task id`, `title=content`, `detail_url="https://app.todoist.com/app/task/{id}"`, `due_date=due.date`의 날짜 부분(없으면 NULL)

### 설정
| 설정 키 (`application.yml`) | 환경변수 | 기본값 |
|-----------------------------|----------|--------|
| `app.alert.jira.base-url` / `email` / `api-token` | `ALERT_JIRA_BASE_URL` / `ALERT_JIRA_EMAIL` / `ALERT_JIRA_API_TOKEN` | 비어 있음(비활성) |
| `app.alert.todoist.api-token` | `ALERT_TODOIST_API_TOKEN` | 비어 있음(비활성) |
| `app.alert.{jira,todoist}.label` | — | `apm_alert` |
| `app.alert.{jira,todoist}.timeout` | — | `10s`(연결·응답 각각) |

- `docker-compose.yml`의 `environment`와 `.env.example`에 4개 환경변수를 추가했다. PRD에서는 `.env`에 실제 값만 채우면 된다.
- 테스트 설정(`src/test/resources/config/application.yml`)은 접속 정보를 비워, 개발 머신에 환경변수가 있어도 테스트가 실제 서비스를 부르지 않는다.

### 주요 파일
- `alert/external/` `ExternalAlertSource`(인터페이스), `ExternalAlert`, `JiraAlertSource`·`JiraProperties`, `TodoistAlertSource`·`TodoistProperties`, `ExternalHttpClients`(타임아웃), `ExternalAlertConfig`
- `alert/service/` `ExternalAlertSyncService`(신규), `AlertService`(새로고침에 연결), `AlertStartupRefresher`(기동 시 조회 추가)
- `application.yml`, 테스트 `config/application.yml`, `docker-compose.yml`, `.env.example`, `README.md`

## 2. 검증

- 전체 테스트 **134건 통과** (이 단계에서 신규 11건, 건너뛴 테스트 없음).
  - `JiraAlertSourceTest`(3) — 가짜 HTTP 서버(`MockRestServiceServer`): 요청 본문(JQL·fields·maxResults·토큰)과 Basic 인증 헤더, `nextPageToken`으로 2페이지 조회, 매핑(기한 없음 포함, baseUrl 끝 `/` 처리), 5xx면 예외, 설정 판정
  - `TodoistAlertSourceTest`(3) — Bearer 헤더, `cursor`로 2페이지 조회(특수문자 `+ / =` 인코딩), 날짜만/날짜시각(`…Z`)/기한 없음 매핑, 401이면 예외, 설정 판정
  - `ExternalAlertSyncTest`(5) — dev DB(롤백), 가짜 소스: DEFAULT 단계로 OPEN upsert와 값, 목록에서 빠진 항목 CLOSE + 닫은 알림은 닫힌 채 유지, 조회 실패 시 기존 알림 유지(FAILED), 미설정이면 DB를 건드리지 않음(SKIPPED), 다른 소스의 알림은 닫지 않음
  - `AlertApiTest`의 새로고침 테스트는 그대로 통과 — 테스트에서는 두 소스 모두 미설정이라 `SKIPPED`
- **실제 Jira/Todoist 호출은 아직 검증하지 않았다**(결정 E 대기). 엔드포인트와 응답 형식은 Atlassian 문서·커뮤니티 자료와 Todoist 공식 Python SDK(`todoist-api-python`) 소스로 확인했다. 토큰이 준비되면 `.env`(PRD) 또는 환경변수(dev)를 넣고 `POST /api/alerts/refresh` 응답과 `alert_item`을 확인한다.
- 서버를 실제로 띄워 보지는 않았다.

## 3. 문서에 없어서 정한 것

1. **Jira는 POST로 검색한다.** JQL을 URL 쿼리에 넣으면 인코딩 문제가 생길 수 있어 요청 본문에 담는다(같은 엔드포인트가 GET·POST를 모두 지원).
2. **페이지 상한 50(Jira 5,000건 / Todoist 10,000건)을 넘으면 실패로 본다.** 일부만 가져온 채 반영하면 나머지 알림이 잘못 CLOSE되기 때문에, 중간 실패와 마찬가지로 아무것도 바꾸지 않는다. 같은 페이지 토큰이 반복되는 경우도 그 자리에서 멈춘다.
3. **Todoist `due.date`는 날짜 부분만 쓴다.** 날짜시각·UTC 형식이 섞여 오지만, 알림은 날짜 단위로 정렬하고 `daysLeft`를 계산하기 때문이다(Jira `duedate`도 날짜만 있다).
4. **Todoist 상세 URL은 `https://app.todoist.com/app/task/{id}`.** 공식 SDK는 제목 슬러그를 앞에 붙이지만 id만으로도 열린다.
5. **Jira 설정은 주소·이메일·토큰 셋이 모두 있어야 활성.** 하나라도 비면 SKIPPED.
6. **조회 실패는 WARN, 저장 실패는 ERROR.** 조회 실패는 외부 사정(네트워크·토큰 만료)이라 WARN이고, 로그에는 예외 요약만 남긴다(스택트레이스 없음). 우리 DB에 쓰다 실패한 것은 ERROR(스택트레이스 포함)다. 응답의 `reason`은 `fetch failed` / `save failed`만 쓰고 내부 내용은 싣지 않는다.
7. **기동 시 외부 조회는 동기로 실행한다.** `ApplicationReadyEvent`는 서버가 요청을 받기 시작한 뒤라 지연이 서비스에 영향을 주지 않고, 타임아웃(10초)이 걸려 있다.
8. **HTTP 클라이언트는 `RestClient`(JDK HttpClient)를 직접 만든다.** 추가 의존성 없이 `spring-web`만 쓰고, 테스트에서는 가짜 서버에 묶은 builder를 넘긴다.
