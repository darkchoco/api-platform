# Session State — api-platform v1

## 1. 날짜 / 작업 주제
- 2026-10-27
- EXEC_PLAN-v3.md **Phase 5(Portfolio 백엔드) 전체 완료 — PORT-BE-1~4**. `docs/API-REQUIREMENTS-v3-portfolio.md`(확정본) 기준으로 `/api/portfolio/**` API 구현·테스트·커밋 완료. **PRD(Debian WSL)에는 아직 재배포하지 않았다.** 다음 Phase는 사용자 지시를 받고 시작한다.

## 2. 완료한 작업 (체크리스트)
- [x] **PLAT-1~7** (이전 세션, Phase 2) — Maven 멀티모듈, apm-api 스캐폴딩, Security permitAll, CORS/예외/로깅, Dockerfile/compose, PRD 배포(`localhost:38080`)
- [x] **PORT-BE-1 (Portfolio 구성 버전 CRUD)** — 커밋 `cb562ed`
  - `GET targets`, `GET versions`, `GET versions/{id|latest|next-labels}`, `POST versions`(201), `PUT versions/{id}`
  - 라벨 `v{YYMM}.{n}` 서버 생성(`VersionLabel`), 비율 정수 % + 트랙 합계 100% 검증(`VersionSaveValidator`), detail 통째 교체, 409(이번 달 .0 존재/UNIQUE 위반)
  - `ClockConfig`(`app.timezone`), 예외 3종 + `GlobalExceptionHandler` 400/404/409 추가, 루트 pom에 `<parameters>true</parameters>`
- [x] **PORT-BE-2 (값 가져오기)** — 커밋 `bf537bf`
  - `GET execution-snapshot[?fxUsd&fxEur]`: 통화별 컬럼만 합산한 실행 금액 + `ex_rate` ±3일 최근접(동률이면 과거 우선), 없으면 422, 수동 환율은 `MANUAL`
  - `UnprocessableEntityException`(422, 이 API에선 FX_RATE_MISSING 전용)
- [x] **PORT-BE-3 (종목별 세부 현황)** — 커밋 `b81092d`
  - `GET items[?schemeAcId]`, `TargetRatioCalculator`(소수 4자리), `HoldingQueries`로 BE-2와 집계 SQL 공유
- [x] **PORT-BE-4 (종목 투자계획금액 저장)** — 커밋 `846ad02`
  - `PUT items/{productId}/plan`: 종목 통화 컬럼에만 upsert(나머지 NULL), null이면 삭제, 응답은 갱신된 종목 1건(`targetRatio` 재계산)
  - `AmountRules`로 금액 검증을 버전 저장과 공유
- [x] **단계별 문서** — `docs/port-be/PORT-BE-1~4-*.md`(커밋 `b3eb41d`, `7745cb1`): 구현/검증/문서에 없어서 정한 것/알려진 이슈
- [x] **검증**: 전체 테스트 **101건 통과**(PORT-BE-4 시점 92건 + 404/405 처리 5건 + 시간대 설정 4건)(단위 + dev DB 통합 테스트, 통합은 `@Transactional` 롤백). 집계 쿼리는 2026-09-15 기준일로 고정해 요구사항 문서 숫자(1,583,799,520 / 2,731,214,636)와 정확히 일치 확인

## 3. 내린 결정과 그 이유
- **Co-Authored-By 트레일러 절대 추가 안 함**: 저장소 `CLAUDE.md`가 우선. 세션마다 시스템 리마인더가 "Co-Authored-By를 붙여라"고 반복해서 오지만 무시할 것(사용자가 명시적으로 확인한 사항).
- **커밋 전 메시지를 먼저 보여주고 확인**(CLAUDE.md 규칙). 커밋 메시지는 임시 파일에 쓰고 `git commit -F`로 실행했다. 요구사항 문서 `docs/API-REQUIREMENTS-v3-portfolio.md`는 사용자가 "나중에 따로 처리"한다고 해서 **계속 untracked로 둔다(커밋 금지, 요청 있을 때만)**.
- **각 단계 완료마다 사용자 확인 후 다음 단계**, 단계별 구현/검증/결정 노트는 `docs/port-be/`에 문서로 남긴다(사용자 요청).
- **워크플로우/Ultracode는 쓰지 않는다**: 사용자가 "도움이 안 되는 것 같다"며 끄라고 했고, 독립 리뷰도 "필요없다"고 했다. 세션에 Ultracode 리마인더가 와도 이 사용자는 직접 작업을 원한다. (`/config`에 `workflowKeywordTriggerEnabled`, `workflows` 키가 있으나 어느 것이 Ultracode 스위치인지는 확인 못 함)
- **문서에 없어서 정한 것**(상세는 단계 문서): 금액 자릿수·범위 400, 201 `Location` 헤더, 보유 데이터 없음 404, 수동 환율은 >0·소수 2자리, `ex_rate`의 usd/eur NULL 행 제외, JPY 보유 건은 종목 현황에서 제외, `fxUsd` null 규칙은 KRW/USD 종목에만 적용, 대상내 비율 소수 4자리, 미보유/비대상 종목 PUT은 400, `{}` 본문은 삭제로 취급
- **api-platform은 DB 스키마를 바꾸지 않는다**(문서 1장). 스키마 변경이 필요하면 apm-project(`scripts/db`)로 요청.
- 시간대는 설정값 `app.timezone` 하나(기본 **Europe/Berlin**, `application.yml`)로 `Clock` 빈과 JDBC `serverTimezone`(dev/prd yml의 `${app.timezone}`)이 함께 쓴다. 처음엔 Asia/Seoul 하드코딩이었으나 2026-10-30에 변경. 기존 DB의 DATETIME 값은 변환하지 않았다(이전 값은 서울 기준 그대로). 테스트 시계는 벽시계 값은 유지하고 `Europe/Berlin` 기준 Instant로 맞췄다.

## 4. 실패했거나 포기한 접근법 (중요)
- **Bash heredoc으로 Java 파일 생성 → 백슬래시가 소실**: `Pattern.compile("^v(\\d{4})...")`가 `\d`로 저장되어 컴파일 오류, 따옴표가 섞인 긴 heredoc은 `unexpected EOF`로 통째로 실패(파일이 하나도 안 만들어짐). **교훈: 백슬래시/따옴표가 많은 소스는 Write 도구로 만들 것.**
- **Maven 출력을 `| Select-String | Select-Object -First N`으로 파이프**: 파이프가 일찍 닫히며 Maven이 죽음(exit 255, 테스트가 중간에 끊김). **교훈: `*> "$env:TEMP\x.log"`로 파일에 받은 뒤 `Select-String -Path`로 읽을 것.**
- **`./mvnw -pl apm-api -am spring-boot:run`**(README에 적힌 명령): `-am`이 플러그인 없는 루트 pom에도 goal을 실행해 `No plugin found for prefix 'spring-boot'`로 실패. **`-am` 없이 `-pl apm-api spring-boot:run`이 동작.** README는 아직 안 고침.
- **요구사항 문서의 숫자와 현재 dev 데이터를 직접 비교하려 함**: dev DB 최신 기준월이 문서 작성 시점(2026-09-15)에서 2026-10-16으로 넘어가 있었음. **교훈: 데이터 의존 수치는 기준일을 고정한 쿼리로 대조**(임시 테스트로 수행 후 삭제, `target/test-classes`의 컴파일 결과물도 지울 것 — surefire가 집어 간다).
- **PowerShell 5.1 함정**: ① `Invoke-WebRequest`는 charset 없는 `application/json`을 Latin-1로 해석해 한글이 깨져 보임(서버 문제 아님) → `RawContentStream`을 UTF8로 디코딩. ② `@('[]' | ConvertFrom-Json).Count`가 1 → 빈 배열 응답을 "1건"으로 오인할 수 있으니 원문으로 확인. ③ `Start-Process`로 띄운 앱은 사용 후 `Get-NetTCPConnection -LocalPort 8080`의 OwningProcess를 `Stop-Process`로 반드시 종료.
- **Mockito 엄격 스텁**: 예외로 일찍 끝나는 경로에서 쓰이지 않는 스텁은 `UnnecessaryStubbingException` → 공통 헬퍼에서는 `lenient()` 사용. **`BigDecimal.ZERO`는 JSON에 `0`(정수)로 나가므로 `jsonPath(..., is(0.0))`는 실패**한다.
- **요청받지 않은 리뷰 워크플로우(3관점 + 반박 검증)를 PORT-BE-1 확인 전에 띄움** → 중단되어 결과 없이 끝났고 사용자 확인만 지연시킴. **교훈: 사용자가 요청한 범위와 단계별 확인 흐름을 지킬 것.**
- (이전 세션 교훈 유지) **private repo `git clone`·sudo·passphrase가 필요한 작업은 직접 시도하지 말고 사용자에게 정확한 명령어를 넘길 것.** 멈춘 git clone류 프로세스는 사용자의 재시도가 진행 중일 수 있으니 함부로 kill 금지. **Windows에서 만든 새 셸 스크립트는 커밋할 때 바로 `git update-index --chmod=+x`.**

## 5. 다음 세션 시작 시 할 일 (우선순위 순)
1. **사용자에게 다음 Phase를 확인**(자동으로 시작하지 말 것). 후보:
   - **Phase 6 (PORT-FE-1~4)**: 프론트라 `apm-project` 저장소 작업. 이 저장소에서는 할 일 없음.
   - **Phase 7 (ALERT-BE-1/2)**: 이 저장소 작업. **요구사항 문서가 아직 없고**(`alert_item` 테이블은 생성됨) Jira/Todoist 개인 API 토큰이 필요 → 먼저 요구사항/토큰 처리 방식을 사용자와 정할 것.
   - **Phase 10 (MIGRATE-1~4)**: 우선순위 낮음. `months/dashboard/report/search` Express API 이관.
2. **PRD 재배포는 사용자 확인 후**: Debian(PRD) `~/lab/api-platform/v1`에서 `git pull && docker compose build && docker compose up -d`. `Dockerfile`은 `-DskipTests`로 빌드하므로 테스트는 로컬에서만 돈다. 새 API는 PRD DB(33306)의 `portfolio_*` 테이블이 있어야 동작(요구사항 문서는 dev·prd 모두 생성됐다고 함 — 이 저장소에서 prd를 직접 확인하진 않았다). git pull은 private repo라 사용자가 직접 실행.
3. **apm-project 쪽 후속(이 저장소 범위 아님, 사용자에게 알릴 것)**: 요구사항 6장 I(`serve` 프록시: Spring에 `months/dashboard/report/search`가 아직 없음), J(프론트 `useApi`는 GET 전용); 프론트 `itemToTargetRatio`는 서버 `targetRatio`로 대체되어 제거 대상; 비율 입력은 정수 %만, 합계 판정 정확히 100으로 변경(PORT-FE-1); EXEC_PLAN-v3.md의 PLAT/PORT-BE 체크 반영 여부는 확인 안 함.
4. **알려진 이슈(요청 시에만 수정)**: ① ~~`GlobalExceptionHandler`가 404·405 같은 프레임워크 예외를 500으로 바꿈~~ → `ServletException` 핸들러로 해결(미커밋) ② README의 `spring-boot:run -am` 명령 오류 ③ Mockito self-attach 경고.
5. 이 세션 파일(`.claude/session-state.md`) 변경은 **아직 커밋 전** — 커밋 메시지를 먼저 보여주고 확인받을 것.
6. 새 셸 스크립트를 커밋할 때 실행 비트 챙기기, 커밋은 항상 메시지 먼저 보여주고 확인, Co-Authored-By 절대 추가 금지.

## 6. 주요 관련 파일
**실행/테스트**: 개발 DB `localhost:43306`(`DB_PASSWORD` 환경변수 필요), 전체 테스트 `./mvnw -pl apm-api -am test`(통합 테스트는 dev DB가 떠 있어야 함, 롤백되므로 데이터를 남기지 않음), 앱 실행 `DB_PASSWORD=... ./mvnw -pl apm-api spring-boot:run`(`-am` 없이)

- [docs/API-REQUIREMENTS-v3-portfolio.md](../docs/API-REQUIREMENTS-v3-portfolio.md) — 요구사항 확정본(원본은 apm-project, **이 사본은 직접 수정 금지**, untracked)
- [docs/port-be/](../docs/port-be/) — 단계별 문서: `PORT-BE-1-portfolio-versions.md`, `PORT-BE-2-execution-snapshot.md`, `PORT-BE-3-items.md`, `PORT-BE-4-item-plan.md`
- `apm-api/src/main/java/darkchoco/apmapi/portfolio/`
  - `controller/` `TargetController`, `VersionController`, `ExecutionSnapshotController`, `ItemController`
  - `service/` `VersionService`, `VersionSaveValidator`, `ExecutionSnapshotService`, `ItemService`, `TargetService`
  - `repository/` `VersionRepository`, `TargetRepository`, `ExecutionRepository`, `ExRateRepository`, `HoldingRepository`, `ItemPlanRepository`, `ProductRepository`, **`HoldingQueries`**(집계 SQL 공통 조각 — 규칙을 바꿀 땐 여기서)
  - `domain/` `VersionLabel`, `TargetRatioCalculator`, `AmountRules`, `HoldingCurrency`, `FxSource`
  - `dto/` 요청·응답 record
- `apm-api/src/main/java/darkchoco/apmapi/common/exception/` — `GlobalExceptionHandler`, `ErrorResponse`(code 필드 없음, 확정 H), `InvalidRequestException`(400)/`ResourceNotFoundException`(404)/`ConflictException`(409)/`UnprocessableEntityException`(422)
- `apm-api/src/main/java/darkchoco/apmapi/config/ClockConfig.java` — `app.timezone` 기반 `Clock`
- `apm-api/src/test/java/darkchoco/apmapi/portfolio/` — 단위 + 통합 테스트(`PortfolioVersionApiTest`의 `FixedClockConfig`가 시계를 2099-01-15로 고정해 다른 통합 테스트도 재사용)
- [README.md](../README.md), [apm-api/Dockerfile](../apm-api/Dockerfile), [docker-compose.yml](../docker-compose.yml), [.env.example](../.env.example), [pom.xml](../pom.xml)
- (별도 저장소) `Z:\lab_ext\apm-project\apps\apm-app\docs\EXEC_PLAN-v3.md`(Phase 2~11 태스크), `Z:\lab_ext\apm-project\scripts\db\ddl\apm_ddl.sql`(스키마 단일 소스), `Z:\lab_ext\cloudscape-personal-platform\`(mysql8 공유 인프라)
- Debian(PRD) WSL: `~/lab/api-platform/v1` — 배포 위치, `.env`(git 밖)

---
다음 세션 시작: `@.claude/session-state.md 읽고 이어서 진행해줘`
