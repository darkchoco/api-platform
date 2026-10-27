# PORT-BE-1 — Portfolio 구성 (버전 CRUD)

- 상태: **완료, 커밋 `cb562ed`**
- 요구사항: [API-REQUIREMENTS-v3-portfolio.md](../API-REQUIREMENTS-v3-portfolio.md) 1장(응답/오류 규약), 2장, 부록 A
- 다음 단계: [PORT-BE-2](PORT-BE-2-execution-snapshot.md)

## 1. 구현

### 엔드포인트
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/portfolio/targets` | `scheme_asset_class`(`scheme_id=2`) 전체, `schemeAcId` 오름차순 |
| GET | `/api/portfolio/versions` | 버전 목록, `versionId` 내림차순. `updatedAt` = `upd_date` 없으면 `ins_date` |
| GET | `/api/portfolio/versions/{versionId}` | 버전 조회. `detail`은 항상 대상 전부(행이 없는 대상은 비율 0, `exec*` null) |
| GET | `/api/portfolio/versions/latest` | `version_id` 최대값 버전. 없으면 404 |
| GET | `/api/portfolio/versions/next-labels[?baseVersionId=]` | 저장 다이얼로그용 라벨 미리보기(표시용) |
| POST | `/api/portfolio/versions` | 새 버전. 201 `{versionId, versionLabel}` + `Location` 헤더 |
| PUT | `/api/portfolio/versions/{versionId}` | 제자리 수정. 200(갱신된 버전 전체), 라벨 불변 |

### 라벨 규칙 (2.2.1)
- 서버가 `v{YYMM}.{n}`으로 생성하고 클라이언트는 라벨을 보내지 않는다.
- `baseVersionId` 없는 POST → 이번 달(Asia/Seoul) `v{YYMM}.0`. 이미 있으면 **409**.
- `baseVersionId` 있는 POST → 기준 버전의 접두사를 유지한 채 같은 접두사 중 최대 `n` + 1. 기준 버전이 없으면 404, 라벨 형식이 `^v\d{4}\.\d+$`가 아니면 400.
- 동시 저장으로 `UNIQUE(version_label)`을 위반하면 `DuplicateKeyException`을 409로 변환한다.

### 저장 검증 (모두 400)
- 금액 ≥ 0, 비율 0~1, 비율은 **정수 %만** 허용(`ratio × 100`이 정수, `0.33` O / `0.333` X). 모두 `BigDecimal`로 비교한다.
- 트랙별 합계(`krwUsdRatio`, `eurRatio`)는 정확히 1. 그 트랙의 계획금이 0 또는 null이면 합 0도 허용(확인 대기 2번 가정대로).
- `detail.schemeAcId`는 `targets`에 있는 값만, 중복 불가.
- `detail`은 트랜잭션 하나로 통째로 교체한다. `ins_date`/`upd_date`는 서버 시각.

### 오류 처리 (`GlobalExceptionHandler`)
`InvalidRequestException`(400), `ResourceNotFoundException`(404), `ConflictException`(409)과 함께 본문 파싱 오류(`HttpMessageNotReadableException`), 경로/쿼리 타입 불일치(`MethodArgumentTypeMismatchException`)를 400으로 처리한다. `ErrorResponse`에 `code`는 추가하지 않았다(확정 H).

### 주요 파일 (`apm-api/src/main/java/darkchoco/apmapi/`)
- `portfolio/controller/` `TargetController`, `VersionController`
- `portfolio/service/` `TargetService`, `VersionService`, `VersionSaveValidator`
- `portfolio/repository/` `TargetRepository`, `VersionRepository`, `VersionHeader`
- `portfolio/domain/VersionLabel` — 라벨 파싱/증가 규칙
- `portfolio/dto/` 요청·응답 record
- `config/ClockConfig` — `Clock`을 Asia/Seoul로 고정
- `common/exception/` 예외 3종과 핸들러

## 2. 검증

- 전체 테스트 **40건 통과** (이 단계에서 신규 35건).
  - 단위: `VersionLabelTest`(4), `VersionSaveValidatorTest`(10), `VersionServiceTest`(11)
  - dev DB 통합: `PortfolioVersionApiTest`(10) — 테스트마다 롤백, 시계를 2099-01로 고정해 기존 라벨과 충돌하지 않게 했다
- 실제 서버를 띄워 읽기 전용 요청으로 확인: targets 7건, versions `[]`(테스트 롤백 확인), latest 404, 존재하지 않는 id 404, `/versions/abc` 400.
- 독립 리뷰(다관점 워크플로우)는 중간에 중단되어 결과 없이 **수행하지 않았다**(사용자 결정).

## 3. 문서에 없어서 정한 것 / 추가한 것

1. **금액 자릿수·범위 검증(400)**: KRW는 `DECIMAL(12)` 범위, USD·EUR·환율은 소수 2자리 + `DECIMAL(12,2)` 범위. 없으면 DB가 조용히 반올림하거나 500이 난다.
2. **201에 `Location` 헤더** 추가(문서에 없음).
3. **PUT은 `baseVersionId`를 무시**한다(요청 record를 POST와 공유).
4. 비율은 DB 스케일 그대로 나간다(`0.600000`). JSON number로는 동일하다.
5. 시각은 초 미만을 버려서 저장한다(DB `DATETIME`이 반올림하는 것 방지).
6. **루트 `pom.xml`에 `<parameters>true</parameters>` 추가**: `spring-boot-starter-parent`를 쓰지 않아 없으면 `@PathVariable int versionId` 등이 런타임에 실패한다.
7. **요구사항 K(시간대) 해소**: `ClockConfig`로 월 판정과 `ins_date`/`upd_date`를 항상 Asia/Seoul로 고정. 개발 PC의 JVM 기본 시간대는 Asia/Seoul이 아니었으므로(서버 로그 시각이 `+01:00`) 고정이 실제로 필요했다.

## 4. 알려진 이슈 (이 단계 범위 밖, 수정하지 않음)

- `GlobalExceptionHandler`의 `Exception` 핸들러가 프레임워크 예외(존재하지 않는 경로의 404, 허용되지 않는 메서드의 405 등)까지 **500으로 바꿔 버린다**.
- README의 `./mvnw -pl apm-api -am spring-boot:run`은 `-am`이 루트 pom에도 goal을 실행해 실패한다. `-am`을 빼면 동작한다.
- 테스트 실행 시 Mockito self-attach 경고가 출력된다(동작에는 영향 없음).
