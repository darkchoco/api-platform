# ALERT-BE-3 — 알림 조회 / 닫기 / 새로고침 API

- 상태: **완료** (커밋은 `git log`의 `ALERT-BE-3` 항목 참고)
- 요구사항: [API-REQUIREMENTS-v3-alert.md](../API-REQUIREMENTS-v3-alert.md) 2장, 3.4, 5장, 결정 A·D
- 이전: [ALERT-BE-1](ALERT-BE-1-maturity.md) / 다음: ALERT-BE-2(Jira/Todoist)

## 1. 구현

### API (`AlertController`, `/api/alerts`)
| 메서드 · 경로 | 응답 | 동작 |
|---------------|------|------|
| `GET /api/alerts` | `200 [AlertResponse]` | 표시 대상(`status='OPEN' AND dismissed_yn='N'`)만. 정렬: 단계 심각도(OVERDUE → URGENT → MILD → DEFAULT) → `dueDate` 오름차순(없으면 마지막) → `alertId`. 그날 첫 조회면 만기 알림을 먼저 재계산 |
| `POST /api/alerts/{alertId}/dismiss` | `204` | `dismissed_yn='Y'`, `dismissed_at=now`. 이미 닫혀 있으면 아무것도 바꾸지 않고 204(멱등). 없는 id `404`, 숫자가 아니면 `400` |
| `POST /api/alerts/dismiss-all` | `200 {"dismissedCount": n}` | 표시 대상 전부를 한 트랜잭션으로 닫음 |
| `POST /api/alerts/refresh` | `200 {maturity, jira, todoist}` | 만기 알림 즉시 재계산. Jira/Todoist는 BE-2 전이라 `SKIPPED` / `not configured` |

- `AlertResponse`: `alertId, sourceType, tier, title, detailUrl, dueDate(yyyy-MM-dd|null), daysLeft(dueDate − 오늘|null)`. `daysLeft`는 서버의 `Clock`(`app.timezone`) 기준.
- 새로고침의 소스별 결과 `SourceRefreshResult`: `OK`면 `status, open, closed`, `FAILED`·`SKIPPED`면 `status, reason`만(null 필드는 생략). 만기 재계산이 실패해도 응답은 200이고 `maturity.status=FAILED`.

### 하루 1회 지연 재계산 (결정 A)
- `MaturityRefreshTracker`가 마지막으로 **성공한** 계산 날짜(`app.timezone`)를 메모리에 둔다. 만기 재계산은 모두 이 클래스를 거친다 — 기동 시(`AlertStartupRefresher`), 목록 조회(`refreshIfNotDoneToday`), 수동 새로고침(`refreshNow`).
- 기동 시 계산도 날짜를 기록하므로, 기동한 날의 첫 조회에서는 다시 계산하지 않는다.
- 동시에 들어온 첫 조회가 같은 날 두 번 계산하지 않도록 `synchronized`(서버 한 대 전제).

### 주요 파일
- `alert/controller/AlertController`
- `alert/service/AlertService`(조회·닫기·새로고침), `MaturityRefreshTracker`(신규), `AlertStartupRefresher`(Tracker를 쓰도록 변경)
- `alert/repository/AlertItemRepository` — `findVisible`, `exists`, `dismiss`, `dismissAllVisible` 추가 / `VisibleAlert`
- `alert/dto/` `AlertResponse`, `DismissAllResponse`, `AlertRefreshResponse`, `SourceRefreshResult` / `alert/domain/SourceRefreshStatus`

## 2. 검증

- 전체 테스트 **123건 통과** (이 단계에서 신규 6건, 건너뛴 테스트 없음).
- dev DB 통합(롤백): `AlertApiTest`(6). 테스트 시작 시 `alert_item`을 비우고(롤백됨) 행을 직접 넣는다. "오늘"을 1900-01-01로 두면 만기 재계산이 알림을 만들지 않아 넣은 행만으로 확인할 수 있다.
  - 목록: 닫힌 행·CLOSE 행 제외, 심각도 → 날짜(null 마지막) → id 정렬, `dueDate`·`daysLeft`(-61)·`detailUrl` 값, null 필드도 키는 내려감
  - 지연 재계산: 그날 첫 조회에서 MILD가 생기고, 같은 날 두 번째 조회는 재계산하지 않으며, 날짜가 바뀐 첫 조회에서 URGENT로 재계산
  - 닫기: 204, 목록에서 빠짐, 다시 닫아도 204이고 `dismissed_at`은 처음 값 유지 / 없는 id 404, `abc` 400
  - 전체 닫기: 표시 대상만 닫고 건수 반환, 다시 호출하면 0
  - 새로고침: `maturity` OK·건수, `jira`/`todoist`는 `{status: SKIPPED, reason: not configured}`만
- **서버를 실제로 띄워 확인하지는 않았다.** HTTP 응답 형태는 MockMvc로 확인했다. 서버를 띄우면 기동 시 재계산이 dev `alert_item`에 실제로 4건(OVERDUE 3 + MILD 1)을 만든다.

## 3. 문서에 없어서 정한 것

1. **조회 전 재계산이 실패해도 목록은 200으로 돌려준다**(지금 DB 기준, ERROR 로그). 날짜를 기록하지 않으므로 다음 조회에서 다시 시도한다. 배너가 통째로 안 뜨는 것보다 하루 늦은 알림이 낫다고 봤다.
2. **닫기는 `upd_date`를 바꾸지 않는다.** `upd_date`는 "마지막 갱신(재계산/재fetch) 시각"이고, 닫은 시각은 `dismissed_at`에 따로 남기 때문이다.
3. **`status='CLOSE'`인 행도 id로 닫을 수 있다**(204). 존재하는 알림이면 닫기는 항상 성공한다.
4. **전체 닫기 전에는 재계산하지 않는다.** 사용자가 마지막으로 본 목록을 닫는 동작이라, 그 사이 새로 생긴 알림까지 보지도 않고 닫지 않게 했다.
5. **Jira/Todoist는 BE-2 전까지 `SKIPPED` / `not configured`.** 설정 키가 아직 없으니 실제로 "설정 없음" 상태이고, BE-2 이후에도 응답 형태가 같다.
6. **`FAILED`의 `reason`에는 예외 메시지를 싣지 않는다**(`maturity refresh failed`). SQL 등 내부 내용은 로그에만 남긴다.
7. **`MaturityRefreshTracker`를 `MaturityAlertService`와 분리했다.** 같은 클래스 안에서 `refresh()`를 부르면 `@Transactional` 프록시를 거치지 않아 트랜잭션이 걸리지 않기 때문이다.
