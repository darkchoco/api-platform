# PORT-BE-2 — 값 가져오기 (실행 금액 + 기준환율)

- 상태: **완료, 커밋 `bf537bf`** (집계 SQL 공통화와 `COALESCE` 보강은 PORT-BE-3 커밋에 포함)
- 요구사항: [API-REQUIREMENTS-v3-portfolio.md](../API-REQUIREMENTS-v3-portfolio.md) 3장
- 이전/다음: [PORT-BE-1](PORT-BE-1-portfolio-versions.md) / [PORT-BE-3](PORT-BE-3-items.md)

## 1. 구현

### 엔드포인트
`GET /api/portfolio/execution-snapshot[?fxUsd=&fxEur=]` — **DB에 쓰지 않는 조회**다. 저장은 사용자가 '저장'을 눌렀을 때 PORT-BE-1의 API로 한다.

```json
{
  "baseDate": "2026-10-16",
  "fxUsd": 1334.19, "fxEur": 1499.50, "fxSource": "EX_RATE", "fxDate": "2026-10-16",
  "fetchDate": "2026-10-27T22:24:48",
  "exec": [ { "schemeAcId": 1, "execKrw": 480352740, "execUsd": 0.00, "execEur": 0.00 }, ... ]
}
```

### 동작
- **기준일**: `stock_curr`와 `investment_curr` 각각의 `MAX(base_date)` 중 늦은 값. 대시보드 월 선택과 무관하다.
- **실행 금액 집계 (핵심)**: `val_krw`는 USD·EUR 보유 건에도 원화 환산액이 채워져 있으므로, 보유 건의 통화(`currency_unit`)에 해당하는 컬럼만 합산한다(KRW→`val_krw`, USD→`val_usd`, EUR→`val_eur`). 대상에 매핑되지 않는 보유 건(연금, 보험 등)과 JPY는 제외된다.
- `exec`는 대상 전부를 `schemeAcId` 순으로 내려주며 보유 건이 없는 대상은 0이다.
- **기준환율**: `ex_rate`에서 기준일 전후 3일 이내 최근접 값. 거리가 같으면 **기준일 이전(과거) 값 우선**(확정 F). 후보를 SQL로 조회한 뒤 자바에서 `거리 → 날짜` 순으로 골라 규칙을 단위 테스트할 수 있게 했다. `fxSource: "EX_RATE"`, `fxDate`는 실제 사용한 `ex_rate.base_date`.
- **환율 누락**: 3일 이내 값이 없으면 **422**. `ErrorResponse`에 `code`를 두지 않기로 해서(확정 H) 메시지에 `FX_RATE_MISSING`을 넣었다.
- **수동 입력**: `fxUsd`·`fxEur`를 함께 주면 환율 조회 없이 `fxSource: "MANUAL"`, `fxDate: null`. 하나만 주면 400.
- 422는 이 API에서 `FX_RATE_MISSING` 한 가지 의미로만 쓴다(`UnprocessableEntityException`의 주석에 명시).

### 주요 파일
- `portfolio/controller/ExecutionSnapshotController`
- `portfolio/service/ExecutionSnapshotService`
- `portfolio/repository/` `ExecutionRepository`(기준일, 집계), `ExRateRepository`, `ExRate`
- `portfolio/domain/FxSource`, `portfolio/dto/` `ExecutionSnapshotResponse`, `ExecAmountDto`
- `common/exception/UnprocessableEntityException` + 핸들러

## 2. 검증

- 전체 테스트 **54건 통과** (이 단계에서 신규 14건).
  - 단위: `ExecutionSnapshotServiceTest`(10) — 최근접 선택, 동률 시 과거 우선, 기준일 당일 우선, 422, 수동 입력 시 조회 생략, 환율 하나만 입력, 0/음수/자릿수 초과, 보유 데이터 없음 404, 대상 0 채움, `fetchDate`
  - dev DB 통합(읽기 전용): `PortfolioExecutionSnapshotApiTest`(4)
- **집계 쿼리 정확성**: dev DB의 최신 기준월이 문서 작성 시점(2026-09-15)에서 2026-10-16으로 넘어가 있어 문서 숫자와 직접 비교할 수 없었다. 같은 쿼리를 기준일 2026-09-15로 고정해 대조한 결과 **문서와 정확히 일치**했다.
  - 통화별 컬럼만 합산한 `execKrw` 합계 **1,583,799,520** (문서 1,583,799,520)
  - 통화 구분 없이 `val_krw`를 합산한 값 2,731,214,636 (문서 2,731,214,636)
  - 이 검증은 임시 테스트로 수행하고 삭제했다(커밋 없음).
- 현재 dev 서버 응답(2026-10-27 확인): `baseDate 2026-10-16`, `EX_RATE`, `fxDate 2026-10-16`, `fxUsd 1334.19`, `fxEur 1499.50`, 대상 7건, `execKrw` 합계 1,500,573,607.

## 3. 문서에 없어서 정한 것

1. **보유 데이터가 전혀 없을 때**(`baseDate`를 정할 수 없을 때)는 **404**. 422는 환율 누락 전용이라 쓰지 않았다.
2. **수동 환율은 0보다 커야 하고 소수 2자리까지**(위반 시 400). 환율 0은 계획금 환산이 무의미하고, 저장 컬럼이 `DECIMAL(12,2)`이다.
3. **`ex_rate`의 `usd` 또는 `eur`가 NULL인 행은 후보에서 제외**한다. 그렇지 않으면 가장 가까운 행이 NULL이어서 환율이 비어 나갈 수 있다.
4. `baseDate`·`fxDate`는 날짜(`yyyy-MM-dd`), `fetchDate`는 `app.timezone`(기본 Europe/Berlin) 기준 초 단위 시각이다.
5. 환율 비교는 시각이 아니라 **날짜 단위**(`ex_rate.base_date`의 날짜 부분)로 한다.
6. (PORT-BE-3 커밋에서 보강) `val_usd`/`val_eur`가 NULL인 행만 있는 대상에서 합계가 NULL로 나가지 않도록 `COALESCE`를 추가했다.

## 4. 후속/참고

- `fetchDate`는 PORT-BE-1의 저장 요청 본문에 그대로 실어 보낼 수 있는 형식이다.
- 집계 SQL은 PORT-BE-3과 `HoldingQueries`로 공유한다 — 규칙(최신 기준월, 활성 보유, 대상 매핑)을 바꿀 때 한 곳만 고치면 된다.
