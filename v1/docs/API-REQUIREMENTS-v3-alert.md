# API 요구사항 — Info/Alarm (Phase 7: ALERT-BE-1~3)

- 상태: **확정본 (2026-11-04)** — 결정 A~D 반영 완료(6장). E(Jira/Todoist 접속 정보)만 나중에 준비한다 — 그 전에는 ALERT-BE-2를 토큰 없이 "소스 비활성"으로 두고 BE-1·BE-3부터 진행해도 된다
- 대상 독자: `api-platform` 저장소(`apm-api` 모듈)를 개발하는 **별도 세션**. 이 문서만으로 작업할 수 있도록 작성했다(필요한 테이블 구조는 부록 A)
- **원본은 apm-project 저장소**(`apps/apm-app/docs/API-REQUIREMENTS-v3-alert.md`)이다. `api-platform/docs/`에 있는 것은 전달용 사본이므로 요구사항이 바뀌면 원본을 고치고 다시 복사한다. 사본을 직접 고치지 말 것
- 참고 문서 (apm-project 저장소, 없어도 됨): `apps/apm-app/docs/PRD-v3.md` 5.2, `PRD-v3-open-questions.md` 4/5/8번, DB 스키마 원본 `scripts/db/ddl/apm_ddl.sql`
- 응답/오류/시간대 규약은 Portfolio API(`API-REQUIREMENTS-v3-portfolio.md`)와 같다: JSON camelCase, 날짜시간 ISO-8601, 오류는 `ErrorResponse(timestamp, status, error, message, path)`, **날짜 판정과 `upd_date` 등은 모두 `app.timezone`(Europe/Berlin)의 `Clock`** 을 쓴다. 인증은 지금 permitAll

## 1. 범위와 전제

| 태스크 | 내용 |
|--------|------|
| ALERT-BE-1 | 만기일(MATURITY) 티어 계산 — `alert_item` upsert (MILD/URGENT/OVERDUE) |
| ALERT-BE-2 | Jira/Todoist 연동 — `apm_alert` 라벨이 붙은 항목만 가져와 `alert_item`에 upsert |
| ALERT-BE-3 | 알림 조회 / 개별 닫기 / 전체 닫기 API |

- **범위 밖**: Dashboard 배너 화면(ALERT-FE-1, apm-project 쪽), 반복 실패 시 별도 알림(PRD 남은 사항)
- **DB**: `alert_item`은 dev·prd 모두 생성 완료(현재 0건). 스키마는 apm-project의 `scripts/db`가 단일 소스이며 **api-platform에서 스키마를 바꾸지 않는다**(필요하면 이 저장소로 요청)
- **경로**: 프론트의 `serve`/Vite가 `/api/*`를 경로 변경 없이 전달한다 → 컨트롤러는 **`/api/alerts/...`** 로 매핑한다. 단, v3 `serve`는 지금 `/api/portfolio`만 Spring으로 보내므로 **`/api/alerts`도 Spring으로 라우팅하도록 apm-project 쪽 `serve`/Vite를 고치는 일은 프론트 쪽 후속**이다(이 문서의 범위 밖)

## 2. `alert_item` 공통 규칙

(설계 근거: PRD-v3-open-questions.md 4/5/8번)

- **노출 규칙**: `status='OPEN' AND dismissed_yn='N'`인 행만 표시한다. 예외 없는 규칙 하나로 통일한다
- **upsert 기준**: `UNIQUE (source_type, source_key, tier)`
- **조건에서 벗어나면** `status='CLOSE'`로 바꾼다. `dismissed_yn`은 건드리지 않는다(자연 소멸이므로)
- **다시 떠야 하는 경우**(예: 만기 알림이 MILD → URGENT로 escalation)는 기존 행을 되돌리지 않고 **다른 `tier`의 새 행**을 만든다. 새 행은 `dismissed_yn='N'`이라 자연스럽게 다시 노출된다. 별도의 "재오픈" 상태나 예외 로직을 만들지 말 것
- **닫은 알림은 같은 (source, key, tier)로 다시 OPEN이 되어도 닫힌 채로 유지**한다(`dismissed_yn='Y'`를 되돌리지 않음). 사용자가 닫은 것을 서버가 다시 띄우지 않는다
- `upd_date`는 upsert로 갱신할 때마다 `now(Clock)`

## 3. ALERT-BE-1 — 만기일(MATURITY)

### 3.1 대상과 티어
`investment` 중 `active_yn='Y'` AND `last_due_date IS NOT NULL`. **보유 여부는 `active_yn`만 본다** — `close_date` 등 다른 컬럼은 조건에 넣지 않는다(결정 B: `active_yn`이 더 정확한 값이다). `stock`에는 만기 컬럼이 없어 대상이 아니다.

`일수 d = 만기일(날짜) − 오늘(날짜)` (오늘 = `LocalDate.now(clock)`, `last_due_date`는 `DATETIME`이지만 날짜 부분만 쓴다)

| 조건 | tier |
|------|------|
| d < 0 | `OVERDUE` (만기 경과) |
| 0 ≤ d ≤ 14 | `URGENT` (14일 전 ~ 당일) |
| 15 ≤ d ≤ 30 | `MILD` (30 ~ 15일 전) |
| d > 30 | 알림 없음 |

### 3.2 `alert_item` 값
| 컬럼 | 값 |
|------|----|
| `source_type` | `'MATURITY'` |
| `source_key` | `invest_id` (문자열) |
| `tier` | 위 표 |
| `status` | `'OPEN'` |
| `title` | `{product.product_nm} · {금융기관명}` — 금융기관명은 `investment.acct_id` → `acct.fin_inst_id` → `fin_inst.fin_inst_nm`. 예: `발행어음 만기형_개인(365) · 미래에셋증권` |
| `detail_url` | `NULL` |
| `due_date` | `last_due_date` |

### 3.3 재계산 알고리즘 (한 번의 호출 = 트랜잭션 하나, 멱등)
1. 3.1 대상 전부를 읽어 각 투자건의 현재 tier를 계산한다
2. 각 투자건의 (MATURITY, invest_id, 현재 tier)를 **upsert**: 없으면 `OPEN`, `dismissed_yn='N'`으로 insert. 있으면 `status='OPEN'`, `title`, `due_date`, `upd_date`만 갱신(`dismissed_yn`은 유지)
3. 같은 투자건의 **다른 tier에 열려 있는 행은 `CLOSE`** (예: URGENT로 올라가면 기존 MILD 행 CLOSE)
4. 계산 결과에 **없는 열린 MATURITY 행은 모두 `CLOSE`** (보유 종료·만기일 삭제·30일 초과 등)

### 3.4 실행 시점
- 서비스 기동 시(`ApplicationReadyEvent` 등) 1회. 실패해도 **기동을 막지 않는다**(로그만)
- **하루 1회 지연 재계산**(결정 A): 서비스가 마지막으로 계산한 날짜가 오늘(`app.timezone`)과 다르면 그날 첫 `GET /api/alerts` 때 재계산한 뒤 응답한다
- 수동 실행: `POST /api/alerts/refresh`(5.4)

### 3.5 현재 dev 데이터 기준 예시 (2026-10-31)
활성 + 만기일 있는 11건 중(2026-11-04 기준): OVERDUE 3건(`invest_id` 27 KB국민 내집마련주택부금 2001-01-19, 10 드림재테크저축보험B3 2024-09-01, 127 발행어음 만기형 2025-12-16), MILD 1건(125 DB저축 M-정기적금 2026-11-28), 나머지 7건은 30일 초과로 알림 없음. 처음 기동하면 OVERDUE 3건과 MILD 1건, 총 4건의 알림이 생긴다.

## 4. ALERT-BE-2 — Jira / Todoist

### 4.1 실행과 실패 처리
- **서비스 기동 시 1회**(MATURITY 계산과 같은 시점, 비동기 실행해도 됨). 별도 주기 스케줄은 만들지 않는다. 수동 실행은 5.4 `POST /api/alerts/refresh`
- 소스(Jira, Todoist)는 **서로 독립**이다. 한 소스가 실패해도 다른 소스와 기동에 영향이 없다
- **실패 시 조용히 스킵 + 로그**(WARN, 토큰은 로그에 쓰지 않는다)하고 **그 소스의 기존 `alert_item`은 건드리지 않는다**(마지막 성공 데이터 유지 — 알림이 갑자기 사라지지 않게). 타임아웃을 둘 것(예: 10초)
- 설정이 비어 있는 소스는 **비활성**으로 보고 건너뛴다(로그 INFO). 로컬 개발에서 토큰 없이도 기동되어야 한다

### 4.2 설정 (비밀 포함)
토큰은 코드·git에 두지 않고 환경변수로 받는다. 제안 키:

| 키 | 설명 |
|----|------|
| `ALERT_JIRA_BASE_URL` | Jira Cloud 주소 (예: `https://xxx.atlassian.net`) |
| `ALERT_JIRA_EMAIL`, `ALERT_JIRA_API_TOKEN` | Basic Auth (이메일 + API 토큰) |
| `ALERT_TODOIST_API_TOKEN` | Todoist 개인 토큰 |
| 라벨 | 양쪽 모두 `apm_alert` (설정값으로 두되 기본 `apm_alert`) |

`docker-compose.yml`은 환경변수를 자동으로 컨테이너에 전달하지 않는다(`APP_TIMEZONE` 때와 같음) → PRD에서 쓰려면 `environment`에 추가하고 `.env`로 주입해야 한다. 실제 토큰 값은 사용자가 PRD `.env`에 직접 넣는다.

### 4.3 Jira (Cloud)
- 인증: Basic (`email:apiToken`)
- 조회: `labels = apm_alert AND statusCategory != Done` (완료된 이슈는 가져오지 않는다). 현재 Jira Cloud의 JQL 검색 REST API를 쓰고(구 `/rest/api/3/search`는 폐지 진행 중이라 구현 시 최신 엔드포인트를 확인할 것), 페이지네이션을 처리한다. 필요한 필드: `summary`, `duedate`
- 매핑: `source_type='JIRA'`, `source_key=이슈키`, `tier='DEFAULT'`, `title='{key} {summary}'`, `detail_url='{baseUrl}/browse/{key}'`, `due_date=duedate`(없으면 `NULL`)

### 4.4 Todoist
- 인증: `Authorization: Bearer {token}`
- 조회: 라벨이 `apm_alert`인 **미완료 태스크**. Todoist API 버전별로 엔드포인트가 다르므로 구현 시 현재 지원되는 버전(라벨 필터가 있는 것)을 확인할 것
- 매핑: `source_type='TODOIST'`, `source_key=task id`, `tier='DEFAULT'`, `title=content`, `detail_url=태스크 URL`, `due_date=due.date`(없으면 `NULL`)

### 4.5 upsert
소스별로 한 트랜잭션: 가져온 항목은 (source_type, source_key, 'DEFAULT')로 upsert(`OPEN`, 표시 필드·`upd_date` 갱신, `dismissed_yn` 유지). **이번에 가져온 목록에 없는 기존 열린 항목은 `CLOSE`** (이슈 완료·삭제·라벨 제거). 소스 호출이 성공한 경우에만 수행한다(4.1).

## 5. ALERT-BE-3 — 알림 API

### 5.1 목록
`GET /api/alerts` → **표시 대상**(`status='OPEN' AND dismissed_yn='N'`)만, 아래 순서로 정렬한다.

정렬: `tier` 심각도(`OVERDUE` → `URGENT` → `MILD` → `DEFAULT`) → `due_date` 오름차순(`NULL`은 마지막) → `alertId`

```json
[
  { "alertId": 7, "sourceType": "MATURITY", "tier": "OVERDUE",
    "title": "발행어음 만기형_개인(365) · 미래에셋증권",
    "detailUrl": null, "dueDate": "2025-12-16", "daysLeft": -319 },
  { "alertId": 9, "sourceType": "JIRA", "tier": "DEFAULT",
    "title": "APM-42 환율 알림 확인",
    "detailUrl": "https://xxx.atlassian.net/browse/APM-42", "dueDate": null, "daysLeft": null }
]
```
- `dueDate`: `yyyy-MM-dd` 또는 `null`
- `daysLeft`: `dueDate − 오늘`(일수, 음수 = 경과). `dueDate`가 없으면 `null`. 서버가 계산해 내려준다(프론트와 서버의 "오늘"이 어긋나는 것을 막기 위함)
- 이 응답은 프로토타입 배너가 쓰는 항목(`tier`, 표시 문구)에 대응한다. `DEFAULT` tier(Jira/Todoist)는 프론트가 소스명 칩으로 표시한다
- 읽기 전용이다. 단 3.4의 하루 1회 지연 재계산이 필요한 날의 첫 조회에서는 먼저 재계산(DB 갱신)한 뒤 응답한다

### 5.2 개별 닫기
`POST /api/alerts/{alertId}/dismiss` → `204 No Content`
- `dismissed_yn='Y'`, `dismissed_at=now`로 갱신한다. 이미 닫혀 있어도 **멱등**(204, 시각은 갱신하지 않음)
- `alertId`가 없으면 `404`, 숫자가 아니면 `400`
- 되돌리기(un-dismiss) API는 만들지 않는다

### 5.3 전체 닫기
`POST /api/alerts/dismiss-all` → `200 { "dismissedCount": 3 }`
- **현재 표시 대상**(5.1과 같은 조건)을 모두 닫는다. 한 트랜잭션. 표시 대상이 없으면 `dismissedCount: 0`

### 5.4 수동 새로고침 (결정 D)
`POST /api/alerts/refresh` → `200`
- MATURITY 재계산(3.3)과 Jira/Todoist 재조회(4장)를 **즉시 실행**하고 결과 요약을 돌려준다. 소스별 실패는 응답에 담고 전체를 5xx로 만들지 않는다
- `status`는 `OK` / `FAILED` / `SKIPPED`(설정 없음). 열림/닫힘 건수는 이번 실행에서 `OPEN`으로 upsert된 수와 `CLOSE`로 바뀐 수

```json
{ "maturity": { "status": "OK", "open": 4, "closed": 0 },
  "jira":     { "status": "SKIPPED", "reason": "not configured" },
  "todoist":  { "status": "OK", "open": 2, "closed": 1 } }
```

## 6. 확정된 결정 (2026-11-04)

| ID | 결정 |
|----|------|
| **A** | MATURITY 재계산은 **기동 시 + 하루 1회**: 서비스가 마지막으로 계산한 날짜(`app.timezone` 기준)가 오늘과 다르면, 그날 첫 `GET /api/alerts` 때 재계산한 뒤 응답한다. 서비스는 오래 상시 구동되므로 기동 시에만 계산하면 MILD → URGENT → OVERDUE 반영이 며칠씩 늦기 때문이다. Jira/Todoist는 PRD대로 기동 시에만 가져온다(D의 수동 새로고침 제외). 마지막 계산 날짜는 메모리에 두면 된다(재기동하면 어차피 다시 계산) |
| **B** | 만기 대상 여부는 **`active_yn`만** 본다. `close_date`는 조건에 넣지 않는다 (`active_yn`이 더 정확하다는 사용자 확인, 문제가 있던 데이터는 수정 완료) |
| **C** | 아주 오래전에 만기가 지난 건(2001년, 2024년 등)도 **그대로 `OVERDUE`로 띄운다.** 컷오프 없음. 사용자가 한 번 닫으면 같은 (건, tier)는 다시 뜨지 않는다 |
| **D** | `POST /api/alerts/refresh`를 **둔다**: MATURITY 재계산 + Jira/Todoist 재조회를 즉시 실행하고 결과 요약을 돌려준다. 상세는 5.4. 프론트 버튼은 지금 만들지 않는다 |
| **E** | **나중에 준비**: Jira Cloud 주소·이메일·API 토큰, Todoist 토큰, `apm_alert` 라벨이 붙은 테스트 항목. 값은 문서에 적지 않는다. 준비되기 전에는 4.1대로 해당 소스가 비활성(SKIPPED)으로 동작하면 된다 |

### 구현 순서 제안
ALERT-BE-1(만기일) → ALERT-BE-3(조회/닫기, 기동 시 계산이 이미 있어야 의미가 있음) → ALERT-BE-2(Jira/Todoist, E 준비 후 실제 연동 검증). BE-2는 코드·테스트까지는 토큰 없이 진행할 수 있다(외부 호출은 mock).

### apm-project 쪽 후속 (api-platform 작업 범위 아님)
- `serve`와 Vite 프록시에 `/api/alerts`를 Spring으로 라우팅 추가
- `AlarmBanner`를 실연동(ALERT-FE-1): 목록 조회, 개별/전체 닫기(`POST`가 `204`를 돌려주므로 `apiSend`가 빈 본문을 허용하도록 보완)

## 부록 A. 참조 테이블 구조 (발췌)

스키마의 단일 소스는 apm-project의 `scripts/db/ddl/apm_ddl.sql`이다. 아래는 구현에 필요한 부분만 옮긴 발췌본이며, 다르게 보이면 실제 DB를 기준으로 하고 알려 달라. DB는 MySQL 8, database `apm`.

```sql
-- 알림 항목 (쓰기)
CREATE TABLE alert_item (
    alert_id     INT AUTO_INCREMENT PRIMARY KEY,
    source_type  VARCHAR(20)  NOT NULL,                 -- MATURITY / JIRA / TODOIST
    source_key   VARCHAR(100) NOT NULL,                 -- invest_id / 이슈 키 / task id
    tier         VARCHAR(20)  DEFAULT 'DEFAULT' NOT NULL, -- MATURITY만 MILD / URGENT / OVERDUE
    status       VARCHAR(10)  NOT NULL,                 -- OPEN / CLOSE (조건이 현재도 유효한지)
    title        VARCHAR(255) NOT NULL,
    detail_url   VARCHAR(255) NULL,
    due_date     DATETIME     NULL,                     -- 정렬 기준일
    dismissed_yn VARCHAR(1)   DEFAULT 'N' NOT NULL,     -- 사용자 닫기 여부
    dismissed_at DATETIME     NULL,
    upd_date     DATETIME     NOT NULL,                 -- 마지막 갱신(재계산/재fetch) 시각
    CONSTRAINT uindex_alert_item_source_type_source_key_tier UNIQUE (source_type, source_key, tier)
);

-- 읽기 전용 -----------------------------------------------------------
CREATE TABLE investment (            -- 예금·채권·펀드 등 비주식 보유 건
    invest_id     INT AUTO_INCREMENT PRIMARY KEY,
    product_id    INT        NOT NULL,        -- FK → product
    acct_id       INT        NOT NULL,        -- FK → acct
    active_yn     VARCHAR(1) DEFAULT 'Y' NOT NULL,
    currency_unit VARCHAR(10) NOT NULL,
    last_due_date DATETIME   NULL,            -- 만기일
    close_date    DATETIME   NULL             -- 종료일
    /* ... */
);
CREATE TABLE product (
    product_id INT AUTO_INCREMENT PRIMARY KEY,
    product_nm VARCHAR(255) NOT NULL
    /* ... */
);
CREATE TABLE acct (
    acct_id     INT AUTO_INCREMENT PRIMARY KEY,
    fin_inst_id INT NOT NULL                  -- FK → fin_inst
    /* ... */
);
CREATE TABLE fin_inst (
    fin_inst_id INT AUTO_INCREMENT PRIMARY KEY,
    fin_inst_nm VARCHAR(255) NULL             -- 판매처명 (예: 미래에셋증권)
);
```
