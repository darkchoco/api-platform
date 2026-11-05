# API 요구사항 — Portfolio (Phase 5: PORT-BE-1~4)

- 상태: **확정본 (2026-10-27)** — 결정 사항 B~H 반영 완료(6장 참고). 6장의 "확인 대기" 2건만 남았고 구현을 막지 않는다
- 대상 독자: `api-platform` 저장소(`apm-api` 모듈)를 개발하는 **별도 세션**. 이 문서는 요구사항만 정의하고 구현은 그쪽에서 한다
- **이 문서만으로 작업할 수 있도록 작성했다.** 필요한 테이블 구조는 부록 A에 발췌해 두었고, 아래 "참고 문서"는 배경 설명일 뿐 읽지 않아도 구현에 지장이 없다
- **원본은 apm-project 저장소**(`apps/apm-app/docs/API-REQUIREMENTS-v3-portfolio.md`)이다. `api-platform/docs/`에 있는 것은 전달용 사본이므로, 요구사항이 바뀌면 원본을 고치고 사본을 다시 복사한다. 사본을 직접 고치지 말 것
- 참고 문서 (apm-project 저장소, 없어도 됨): `apps/apm-app/docs/PRD-v3.md` 5.3, `PRD-v3-open-questions.md` 1~3번, `EXEC_PLAN-v3.md` Phase 5, DB 스키마 원본 `scripts/db/ddl/apm_ddl.sql`
- 프론트 쪽 데이터 형태의 기준: v3 프로토타입(`feat/v3-frontend` 브랜치)의 `mockData.ts`. **아래 JSON 필드명은 이 mock 인터페이스와 동일하게 맞췄다.** 프론트 코드는 api-platform에서 볼 필요가 없고, 필드명은 이 문서의 JSON 예시를 따르면 된다

## 1. 범위와 전제

| 태스크 | 내용 |
|--------|------|
| PORT-BE-1 | Portfolio 구성 CRUD — 버전 저장/조회, 대상별 계획 비율 |
| PORT-BE-2 | '값 가져오기' — 최신 기준월 실행 금액 + 기준환율 조회 |
| PORT-BE-3 | 종목별 세부 현황 조회 |
| PORT-BE-4 | 종목별 투자계획금액 입력/저장, 대상내 비율 톱다운 계산 |

- **범위 밖**: Info/Alarm(Phase 7), 기존 v2 API 이관(Phase 10)
- **DB**: 테이블 4개(`portfolio_version`, `portfolio_plan_detail`, `portfolio_item_plan`, `alert_item`)는 dev·prd 모두 생성 완료. 이 요구사항은 앞 3개를 쓴다. 스키마는 `scripts/db`가 단일 소스이며 **api-platform에서 스키마를 바꾸지 않는다**(필요하면 이 저장소로 요청)
- **경로**: 프론트의 `serve`가 `/api/*`를 경로 변경 없이 `http://localhost:38080`으로 프록시한다 → 컨트롤러는 **`/api/portfolio/...`** 로 매핑한다
- **인증**: 지금은 permitAll (PLAT-3). 이 문서의 API에도 인증을 요구하지 않는다
- **코드 컨벤션**: api-platform의 `CLAUDE.md`·`docs/spring-boot-conventions.md`를 따른다 (패키지 `darkchoco.apmapi`, 가독성 우선)

### 응답/오류 규약
- JSON 필드는 **camelCase**, 금액·비율은 JSON number. 비율은 0~1 소수(예: 25% → `0.25`, DB `DECIMAL(9,6)`). 단 'Portfolio 구성'에서 입력하는 비율은 **정수 %만 허용**하므로 소수 둘째 자리까지만 유효하다(`0.33` O, `0.333` X). 서버는 `BigDecimal`로 검증할 것(부동소수점 비교 금지)
- 원화 금액은 정수(`DECIMAL(12)`), 달러·유로 금액·환율은 소수 2자리(`DECIMAL(12,2)`)
- 오류는 기존 `ErrorResponse(timestamp, status, error, message, path)`를 쓴다. 현재 `GlobalExceptionHandler`는 400(검증)·500만 처리하므로, 아래 **404 / 409 / 422용 핸들러를 추가**해야 한다. `ErrorResponse`에 `code` 필드는 **추가하지 않는다**(확정 H). 프론트는 HTTP 상태로 분기한다 — 이 API에서 **422는 `FX_RATE_MISSING` 한 가지**, **409는 버전 라벨 충돌 한 가지**로만 쓰도록 유지할 것. 이후 같은 상태에 다른 의미가 생기면 그때 `code`를 도입한다
- 이 API는 파생값(계획금액, 실행율 등)을 **저장하지도 응답에 넣지도 않는다** — 원시값만 주고 프론트(`calc.ts`)가 계산한다. 예외는 종목의 `targetRatio`(PORT-BE-4, 서버 계산)

## 2. PORT-BE-1 — Portfolio 구성 (버전)

### 2.1 대상 목록
`GET /api/portfolio/targets` → `scheme_asset_class`(`scheme_id=2`) 전체, `schemeAcId` 오름차순

```json
[ { "schemeAcId": 1, "name": "국내주식/ETF" }, { "schemeAcId": 2, "name": "국내채권" }, ... ]
```
현재 7건(1 국내주식/ETF, 2 국내채권, 3 금/원자재, 4 예금/발행어음, 5 해외주식/ETF, 6 해외채권, 7 현금/CMA/RP). 화면 표시 순서는 **프론트 상수**로 정한다(확정 G) — 서버는 `schemeAcId` 순으로만 준다.

### 2.2 버전 목록
`GET /api/portfolio/versions` → **`versionId` 내림차순**(= 생성 순 최신순). ②의 콤보박스(버전 번호 | 최종수정일)와 저장 다이얼로그의 "가장 최근 버전" Tip에 쓴다

```json
[ { "versionId": 3, "versionLabel": "v2608.0", "updatedAt": "2026-08-01T00:00:00" }, ... ]
```
`updatedAt` = `upd_date`가 있으면 그 값, 없으면 `ins_date`.

### 2.2.1 버전 라벨 규칙 (확정 B)
라벨은 사용자가 자유 입력하지 않는다. **서버가 규칙대로 생성**하며 형식은 `v{YYMM}.{n}`이다 (예: `v2610.0`, `v2610.1`).

| 저장 경로 | 라벨 결정 |
|-----------|-----------|
| 'Portfolio 구성'에서 저장 (신규) | 현재 월(서버 시각, Asia/Seoul) 기준 `v{YYMM}.0` |
| '조회 및 업데이트' 저장 ① "기존 버전 업데이트" | 라벨 변경 없음 (제자리 수정) |
| '조회 및 업데이트' 저장 ② "새 버전으로 저장" | 기준 버전의 라벨을 읽어 `.n`을 올린다: `v2610.0` → `v2610.1`. **같은 `YYMM` 접두사를 가진 버전 중 최대 `n` + 1**을 쓴다 (이미 `v2610.1`이 있으면 `v2610.2`) |
| '조회 및 업데이트' 저장 ③ "이번 달 기준으로 새 버전 시작" | 'Portfolio 구성' 경로와 같은 규칙: 현재 월 기준 `v{YYMM}.0` |

- ②는 월이 바뀌어도 **기준 버전의 접두사를 유지**한다 (`v2610.3`을 11월에 기준으로 저장하면 `v2610.4`). 새 월로 넘어가려면 ③을 쓴다 (`v2611.0`)
- ③은 같은 달이면 `v{YYMM}.0`이 이미 존재할 가능성이 크다(예: 10월에 `v2610.3`을 열었을 때 `v2610.0`). 이 경우 ③은 선택할 수 없는 상태(`available: false`)로 내려준다
- 기준 버전의 라벨이 `^v\d{4}\.\d+$` 형식이 아니면(과거 수동 데이터 등) `400`으로 거절한다
- 정렬·"최신" 판단은 `versionId`로 한다. 한 달에 `.9`를 넘겨 올리는 일은 없다고 보므로(사용자 확인) 라벨 문자열 정렬이 깨질 걱정은 없지만, 월이 바뀐 뒤 이전 월 접두사로 "새 버전으로 저장"하면 라벨 기준 최신(`v2611.0`)과 가장 최근 저장분(`v2610.4`)이 달라지기 때문에 "가장 최근에 저장한 버전"을 뜻하는 `versionId`를 기준으로 삼는다

`GET /api/portfolio/versions/next-labels[?baseVersionId=n]` → 저장 다이얼로그에 **저장될 라벨을 미리 보여주기 위한** 조회. 실제 저장 시 서버가 같은 규칙으로 다시 계산하므로 이 값은 **표시용**이다. 두 화면이 함께 쓴다.
```json
{
  "thisMonth": { "versionLabel": "v2611.0", "available": true },
  "fromBase":  { "versionLabel": "v2610.4", "baseVersionLabel": "v2610.3" }
}
```
- `thisMonth` — 위 표 ③ (= 'Portfolio 구성' 저장 경로). 이미 존재하면 `available: false` (2.4의 409와 같은 조건)
- `fromBase` — 위 표 ②. `baseVersionId`를 주었을 때만 채우고, 없으면 `null`. 기준 버전이 없으면 404. `baseVersionLabel`은 ①에 표시할 기존 버전 라벨이다
- 'Portfolio 구성'은 `baseVersionId` 없이 호출해 `thisMonth`만 쓴다

**화면 흐름 (참고)**: '조회 및 업데이트'의 저장 다이얼로그는 아래 세 선택지를 **저장될 라벨과 함께** 보여준다. 예: 기준 버전 `v2610.3`을 11월에 열었을 때
1. 기존 버전 업데이트 — `v2610.3`
2. 새 버전으로 저장 — `v2610.4`
3. 이번 달 기준으로 새 버전 시작 — `v2611.0` (`available: false`면 비활성)

### 2.3 버전 조회
- `GET /api/portfolio/versions/{versionId}`
- `GET /api/portfolio/versions/latest` — 가장 최근 버전. ①의 시작 화면과 PORT-BE-3의 대상 계획금액 기준. 버전이 하나도 없으면 404. **"최신" = `version_id` 최대값**(확정 C). 과거 버전을 제자리 수정해도 최신이 되지 않는다

```json
{
  "versionId": 3, "versionLabel": "v2608.0",
  "availKrw": 50000000, "availUsd": 20000.00, "availEur": 5000.00,
  "planKrw": 45000000,  "planUsd": 18000.00,  "planEur": 4500.00,
  "fxUsd": 1350.50, "fxEur": 1470.20, "fetchDate": "2026-08-27T09:12:00",
  "insDate": "2026-08-01T00:00:00",
  "detail": [
    { "schemeAcId": 1, "krwUsdRatio": 0.25, "eurRatio": 0,
      "execKrw": 15000000, "execUsd": 0, "execEur": 0 }
  ]
}
```
- `detail`은 **항상 대상 7건 전부**를 `schemeAcId` 순으로 내려준다. DB에 행이 없는 대상은 `krwUsdRatio: 0, eurRatio: 0, exec*: null`로 채운다 (대상이 늘어도 화면이 깨지지 않도록)
- `fxUsd`, `fxEur`, `fetchDate`, `exec*`는 '값 가져오기' 전이면 `null`

### 2.4 저장
- **신규 (새 버전 생성)**: `POST /api/portfolio/versions` → `201 { "versionId": 4, "versionLabel": "v2610.0" }`
  - 본문에 `versionLabel`은 **없다**. 라벨은 서버가 2.2.1 규칙으로 정한다
  - `baseVersionId`가 **없으면** 'Portfolio 구성' 경로이자 '조회 및 업데이트'의 ③ "이번 달 기준으로 새 버전 시작" → `v{YYMM}.0`. 이미 있으면 **409** (메시지: 이번 달 `.0` 버전이 이미 있으니 '조회 및 업데이트'에서 수정하거나 새 버전으로 저장하라는 안내) — 6장 확인 대기 1
  - `baseVersionId`가 **있으면** '조회 및 업데이트'의 ② "새 버전으로 저장" 경로 → 올림 규칙. 기준 버전이 없으면 404
  - 어느 경우든 본문의 내용(수정된 값)이 새 버전에 저장된다. ③은 기준 버전과 무관하게 현재 화면의 값으로 새 버전을 만드는 것이다
  - 동시 저장으로 `UNIQUE(version_label)`을 위반하면 409로 변환한다
- **수정 (제자리)**: `PUT /api/portfolio/versions/{versionId}` → `200` (갱신된 버전 전체) — '조회 및 업데이트'의 "기존 버전 업데이트". 라벨은 바뀌지 않는다
- 요청 본문은 조회 응답에서 `versionId`·`versionLabel`·`insDate`를 뺀 형태 (POST는 추가로 선택 필드 `baseVersionId`). `detail`은 **트랜잭션 하나로 통째로 교체**한다 (`PK (version_id, scheme_id=2, scheme_ac_id)`)
- 서버 설정값: 신규는 `ins_date = now`, 수정은 `upd_date = now`
- 검증 (위반은 모두 **400**):
  - 금액은 0 이상, 비율은 0 이상 1 이하
  - **비율은 정수 %만 허용** (확정 D): `ratio × 100`이 정수여야 한다. `0.33`은 허용, `0.333`은 거부. 합계가 100이 되도록 사용자가 33/33/34처럼 직접 맞춘다 (서버가 나머지를 자동 배분하지 않는다)
  - **비율 합계**: 원화+달러 트랙(`krwUsdRatio`)과 유로 트랙(`eurRatio`) 각각 합이 정확히 `1`(=100%)이어야 한다. 정수 %이므로 허용 오차 없이 `BigDecimal` 동치 비교한다. 해당 통화 트랙을 쓰지 않는 경우(그 트랙의 계획금이 0 또는 `null`)에는 합이 `0`인 것도 허용한다 — 6장 확인 대기 2
  - `detail`의 `schemeAcId`는 `targets`에 있는 값만 허용
  - 프론트 프로토타입은 비율을 소수 1자리(`Math.round(x*1000)/10`)로 입력받고 합계 판정도 오차 0.001로 하고 있다 → PORT-FE-1에서 **정수 입력만 받도록** 바꾸고 판정도 정확히 100으로 맞출 것

## 3. PORT-BE-2 — 값 가져오기

`GET /api/portfolio/execution-snapshot[?fxUsd=...&fxEur=...]`

- **DB에 쓰지 않는다.** 화면 상태를 채우기 위한 조회이며, 저장은 사용자가 '저장'을 눌렀을 때 2.4로 한다. 재실행하면 그냥 다시 계산한 값을 돌려준다
- 기준월에 관계없이(대시보드의 월 선택과 무관) **항상 최신 `base_date`** 를 쓴다: `stock_curr`, `investment_curr` 각각의 `MAX(base_date)`. 두 값이 다를 수 있으나 importer가 같은 날짜로 적재하므로 실제로는 같다. 응답의 `baseDate`는 둘 중 늦은 값

```json
{
  "baseDate": "2026-09-15",
  "fxUsd": 1362.11, "fxEur": 1572.28, "fxSource": "EX_RATE", "fxDate": "2026-09-15",
  "fetchDate": "2026-10-25T09:12:00",
  "exec": [ { "schemeAcId": 1, "execKrw": 563765300, "execUsd": 0.00, "execEur": 0.00 }, ... ]
}
```
`exec`는 대상 7건 전부 (보유 건이 없으면 0).

### 3.1 실행 금액 집계 규칙 (**핵심 — 틀리기 쉬움**)
보유 건(`stock_curr`·`investment_curr`)의 `val_krw`는 **USD·EUR 보유 건에도 항상 원화 환산액이 채워져 있다.** 그래서 통화 구분 없이 `val_krw`를 합산하면 이중 계상된다. 반드시 **보유 건의 통화(`stock.currency_unit` / `investment.currency_unit`)에 해당하는 컬럼만** 합산한다.

| 보유 통화 | 합산 대상 |
|-----------|-----------|
| KRW | `val_krw` → `execKrw` |
| USD | `val_usd` → `execUsd` |
| EUR | `val_eur` → `execEur` |

- dev 최신 기준월(2026-09-15), 대상에 매핑된 보유 건 기준으로 검증: 올바른 `execKrw` 합계 **1,583,799,520** vs. 통화 구분 없이 `val_krw`를 합산한 값 **2,731,214,636** (**72.4% 과대**)
- 대상 매핑: `active_yn='Y'` 보유 건 → `product` → `product_type_mapping` → `scheme_sub_asset_class (scheme_id=2)` → `scheme_asset_class`
- **매핑되지 않는 보유 건은 의도적으로 제외한다** — 연금, 보험/저축보험 등은 Portfolio 대상이 아니다 (dev 기준 해당 건들이 있음). 이 때문에 '투자확정금액' 합계는 전체 자산 합계와 다르다
- JPY 보유 건은 위 세 컬럼에 해당하지 않아 합산되지 않는다 (현재 최신월에 JPY 보유 건 없음)

검증용 참조 쿼리:
```sql
SELECT sac.scheme_ac_id,
       SUM(CASE WHEN h.cur='KRW' THEN h.val_krw ELSE 0 END) AS exec_krw,
       SUM(CASE WHEN h.cur='USD' THEN h.val_usd ELSE 0 END) AS exec_usd,
       SUM(CASE WHEN h.cur='EUR' THEN h.val_eur ELSE 0 END) AS exec_eur
FROM (
  SELECT s.product_id, s.currency_unit AS cur, c.val_krw, c.val_usd, c.val_eur
    FROM stock_curr c JOIN stock s ON s.s_id = c.s_id
   WHERE c.base_date = (SELECT MAX(base_date) FROM stock_curr) AND s.active_yn = 'Y'
  UNION ALL
  SELECT i.product_id, i.currency_unit, c.val_krw, c.val_usd, c.val_eur
    FROM investment_curr c JOIN investment i ON i.invest_id = c.invest_id
   WHERE c.base_date = (SELECT MAX(base_date) FROM investment_curr) AND i.active_yn = 'Y'
) h
JOIN product p                  ON p.product_id = h.product_id
JOIN product_type_mapping m     ON m.product_type_id = p.product_type_id
JOIN scheme_sub_asset_class ssc ON ssc.sub_class_id = m.sub_class_id AND ssc.scheme_id = 2
JOIN scheme_asset_class sac     ON sac.scheme_id = 2 AND sac.scheme_ac_id = ssc.scheme_ac_id
GROUP BY sac.scheme_ac_id;
```

### 3.2 기준환율
- `ex_rate`(`base_date`, `usd`, `eur`)에서 **`baseDate` 전후 3일 이내의 최근접 값**을 쓴다. 응답의 `fxDate`는 실제 사용한 `ex_rate.base_date`, `fxSource`는 `"EX_RATE"`
- 거리가 같은 날짜가 둘이면 **기준일 이전(과거) 값을 우선**한다 (확정 F — 기준일 시점에 알 수 있던 값)
- 3일 이내 값이 없으면 **중단**하고 수동 입력을 요구한다: `422`, 코드 `FX_RATE_MISSING`
- 프론트는 수동 입력값을 `fxUsd`, `fxEur` 쿼리 파라미터로 실어 같은 API를 다시 호출한다. 이때는 환율 조회를 하지 않고 그 값을 쓰며 `fxSource: "MANUAL"`, `fxDate: null`. 둘 중 하나만 오면 400
- 환율은 `portfolio_version.fx_usd/fx_eur`에 저장되므로 출처와 무관하게 같은 필드로 다룬다

## 4. PORT-BE-3 — 종목별 세부 현황 조회

`GET /api/portfolio/items[?schemeAcId=n]`

- **종목 행은 저장하지 않고 호출 시점마다 동적으로 구성**한다: 최신 `base_date`에 `active_yn='Y'`인 보유 건을 `product` 단위로 묶는다. 같은 종목을 여러 계좌에 갖고 있으면 합산한다. 매도·청산된 종목은 자연히 빠진다
- 대상 매핑과 통화별 합산 규칙은 3.1과 동일 (`currentKrw`는 KRW 보유분만, `currentUsd`는 USD 보유분만, `currentEur`는 EUR 보유분만)
- `current*`는 **시가(현재가치)** 기준이다 — `stock`/`investment`의 `curr` 테이블 값이고, `stock.av_quote`류 매입원가는 쓰지 않는다
- **종목은 통화가 하나다** (설계상 보유 종목 : 통화 = 1 : 1, 확정 E). 종목의 통화는 보유 건의 `currency_unit`(`stock`/`investment`)이며, 응답의 `currency`(`"KRW"|"USD"|"EUR"`)로 내려준다. 한 종목의 보유 건들이 서로 다른 통화로 나오면 데이터 오류이므로 500이 아니라 해당 종목을 로그에 남기고 첫 통화를 쓴다
- 투자계획금액은 `portfolio_item_plan`에서 가져오며, 행이 없으면 `null`. 값은 **그 종목의 통화 컬럼에만** 들어 있고(`KRW`→`planKrw`, `USD`→`planUsd`, `EUR`→`planEur`) 나머지 두 컬럼은 `null`
- 정렬: `schemeAcId`, `productNm` 오름차순

```json
[
  { "productId": 1, "schemeAcId": 1, "productNm": "TIGER 200", "currency": "KRW",
    "symbolAlias": "102110", "productNmDetail": "코스피200 추종 ETF",
    "planKrw": 8000000, "planUsd": null, "planEur": null,
    "currentKrw": 8500000, "currentUsd": 0, "currentEur": 0,
    "targetRatio": 0.4617 }
]
```
`targetRatio`는 4.1 참조 (예시 값은 2.3의 예시 버전 기준: 분모 `0.25 × (45,000,000 + 18,000 × 1,350.5) = 17,327,250`, `8,000,000 ÷ 17,327,250 ≈ 0.4617`). 계획금액을 초과한 종목의 붉은색 강조는 프론트가 `current*`와 `plan*`을 비교해 처리한다.

### 4.1 대상내 비율 (`targetRatio`, 톱다운)
`종목 투자계획금액 ÷ 그 종목이 속한 대상의 'Portfolio 구성' 계획금액` — 기준 버전은 **가장 최근 버전**(2.3). 같은 대상 안의 비율 합이 100% 미만일 수 있다 (아직 종목에 배정하지 않은 목표가 남은 것이며 오류가 아니다).

| 종목의 `currency` | 분자 | 분모 |
|---|---|---|
| KRW 또는 USD | `planKrw`, 또는 `planUsd × fxUsd` | `krwUsdRatio × (planKrw + planUsd × fxUsd)` (버전 헤더의 값) |
| EUR | `planEur` | `eurRatio × planEur` (버전 헤더의 `planEur`) |

- `fxUsd`는 **최신 버전에 저장된 값**을 쓴다
- 분모가 0이거나 `fxUsd`가 `null`이면 `targetRatio: null`; 종목 계획이 `null`이면 `null`
- 통화는 `currency`로 정해지므로 "먼저 값이 있는 통화"를 고르는 규칙은 필요 없다 (프로토타입의 KRW → USD → EUR 순서 탐색 로직은 제거 대상)
- 이 계산은 현재 프론트 `PortfolioItemDetailScreen.tsx`의 `itemToTargetRatio`에 있다. 서버로 옮기면 그 함수는 제거한다 (같은 로직 중복 방지)

## 5. PORT-BE-4 — 종목 투자계획금액 저장

`PUT /api/portfolio/items/{productId}/plan`

```json
{ "planAmount": 8000000 }
```
- 본문은 **금액 하나**(`planAmount`)다. 통화는 서버가 종목의 보유 통화(4장 `currency`)로 정하고, 그 통화에 해당하는 컬럼(`KRW`→`plan_krw`, `USD`→`plan_usd`, `EUR`→`plan_eur`)에만 저장하며 나머지 두 컬럼은 `NULL`로 만든다 (확정 E). 클라이언트가 통화를 지정하지 않으므로 "여러 통화가 동시에 입력되는" 상태가 생길 수 없다
- `portfolio_item_plan`에 **upsert** (`product_id` PK), `upd_date = now`. 버전 이력은 두지 않는다(최신값만 유지)
- `planAmount`가 `null`이면 "계획 없음"으로 보고 행을 **삭제**한다
- 응답: 갱신된 종목 1건 (4의 항목 형태, `targetRatio` 재계산 포함) — 인라인 편집 후 화면이 바로 새 비율을 보여주기 위함
- 검증: `productId`가 없으면 **404**, `planAmount`가 음수면 **400**. 원화(`DECIMAL(12)`)는 정수만 허용, 달러·유로는 소수 2자리까지
- **현재 보유 중이 아닌 종목**(활성 보유 건 없음)은 통화를 알 수 없으므로 **400**으로 거절한다. 화면의 종목 목록이 보유 종목만 보여주므로 정상 사용에서는 발생하지 않는다 (매수 전 계획은 현재 범위 밖)

## 6. 열린 이슈

### 확정된 결정 (2026-10-27)
| 항목 | 결정 |
|------|------|
| B. 저장 방식·버전명 | 2.2.1·2.4. 라벨은 서버 생성 `v{YYMM}.{n}`, 임의 이름 불가. 'Portfolio 구성' 저장 = `v{YYMM}.0`; '조회 및 업데이트' 저장 시 ① 기존 버전 업데이트(PUT) ② 새 버전으로 저장(POST+`baseVersionId`) ③ 이번 달 기준으로 새 버전 시작(POST, `baseVersionId` 없음) 중에서 고르게 하며, 각 선택지에 저장될 라벨을 함께 보여준다 (`next-labels`) |
| C. 최신 버전 | `version_id` 최대값 (= 가장 최근에 생성한 버전) |
| D. 비율 입력 | 정수 %만 허용(33/33/34), 합계는 정확히 100 |
| E. 종목 통화 | 종목당 통화 1개(보유 건의 `currency_unit`). 서버가 통화를 정하고 PUT 본문은 `{planAmount}` |
| F. 환율 동률 | 기준일 이전(과거) 값 우선 |
| G. 대상 표시 순서 | 프론트 상수 |
| H. `ErrorResponse.code` | 추가하지 않음. HTTP 상태로 분기 (422 = `FX_RATE_MISSING` 전용, 409 = 버전 라벨 충돌 전용) |

### 확인 대기 (구현을 막지 않는 가정)
1. **이번 달 `v{YYMM}.0`이 이미 있을 때 'Portfolio 구성'에서 저장**하면 409로 거절하고 '조회 및 업데이트'로 안내한다고 가정했다. 자동으로 `.1`을 붙이는 대안은 "새 버전 = 기존 버전을 읽어 올림"이라는 B의 흐름과 섞이므로 택하지 않았다. 프론트는 `next-labels`의 `thisMonth.available`이 `false`이면 저장 버튼 전에 안내를 띄울 수 있다
2. **비율 합계의 예외**: 유로 계획금이 0이면 `eurRatio` 합 0을 허용한다고 가정했다 (D에서 답이 없던 부분). 항상 100을 강제하려면 2.4의 해당 문장만 빼면 된다

### api-platform에서 확인할 것
- **K. 날짜 형식/시간대** — 응답의 날짜시간은 ISO-8601(`yyyy-MM-ddTHH:mm:ss`), 서버 시간대는 Asia/Seoul로 가정했다. JDBC URL에는 `serverTimezone=Asia/Seoul`이 이미 있으나 DB `DATETIME`은 시간대 정보가 없으므로, 'v{YYMM}.0'의 월 판정과 `ins_date`/`upd_date`에 쓰는 시계가 Docker 컨테이너에서도 Asia/Seoul인지 확인할 것

### apm-project 쪽 후속 (**api-platform 작업 범위 아님** — 참고용)
- **I. `serve` 프록시와 기존 v2 API** — v3의 `serve`는 `/api/*` 전체를 38080(Spring)으로 보내는데, Spring에는 `months/dashboard/report/search`가 아직 없다(Express 39100에만 있음, MIGRATE-1~4 대상). 지금 상태로 v3를 배포하면 Dashboard/Report/Search가 동작하지 않는다. 선택지: ① `serve`에서 `/api/portfolio`는 38080, 나머지는 39100으로 경로별 라우팅 ② MIGRATE-1~4를 v3 배포 전에 선행
- **J. 프론트 `useApi`는 GET 전용** — 저장/수정(POST/PUT)용 헬퍼가 필요하다 (PORT-FE-1~4 작업에 포함)

## 부록 A. 참조 테이블 구조 (발췌)

이 API가 읽거나 쓰는 테이블이다. **스키마의 단일 소스는 apm-project의 `scripts/db/ddl/apm_ddl.sql`** 이며, 여기 있는 것은 구현에 필요한 부분만 옮긴 발췌본이다(FK·COMMENT 일부 생략). 스키마가 다르게 보이면 실제 DB를 기준으로 하고 apm-project 쪽에 알려 달라. 이 API는 스키마를 바꾸지 않는다.

DB는 MySQL 8, database `apm`. 기본 접속은 `application-dev.yml`(43306, dev) / `application-prd.yml`(33306, prd).

### A.1 쓰는 테이블 (읽기·쓰기)
```sql
-- 버전 헤더. 라벨은 서버가 v{YYMM}.{n}으로 생성 (2.2.1)
CREATE TABLE portfolio_version (
    version_id    INT AUTO_INCREMENT PRIMARY KEY,
    version_label VARCHAR(50)    NOT NULL,              -- UNIQUE
    avail_krw     DECIMAL(12)    NULL,                  -- 투자가용금
    avail_usd     DECIMAL(12, 2) NULL,
    avail_eur     DECIMAL(12, 2) NULL,
    plan_krw      DECIMAL(12)    NULL,                  -- 투자계획금
    plan_usd      DECIMAL(12, 2) NULL,
    plan_eur      DECIMAL(12, 2) NULL,
    fx_usd        DECIMAL(12, 2) NULL,                  -- 값 가져오기 시점 기준환율
    fx_eur        DECIMAL(12, 2) NULL,
    fetch_date    DATETIME       NULL,                  -- 마지막 값 가져오기 실행 시각
    ins_date      DATETIME       NOT NULL,              -- 최초 저장 시각
    upd_date      DATETIME       NULL,                  -- 마지막 수정 시각
    CONSTRAINT uindex_portfolio_version_label UNIQUE (version_label)
);

-- 버전 × 대상, 대상당 1행
CREATE TABLE portfolio_plan_detail (
    version_id    INT            NOT NULL,              -- FK → portfolio_version
    scheme_id     INT DEFAULT 2  NOT NULL,              -- 고정값 2
    scheme_ac_id  INT            NOT NULL,              -- FK (scheme_id, scheme_ac_id) → scheme_asset_class
    krw_usd_ratio DECIMAL(9, 6)  NULL,                  -- 원화+달러 투자비율 (0~1)
    eur_ratio     DECIMAL(9, 6)  NULL,                  -- 유로 투자비율 (0~1)
    exec_krw      DECIMAL(12)    NULL,                  -- 값 가져오기 스냅샷
    exec_usd      DECIMAL(12, 2) NULL,
    exec_eur      DECIMAL(12, 2) NULL,
    PRIMARY KEY (version_id, scheme_id, scheme_ac_id)
);

-- 종목별 투자계획금액, 최신값만 유지. 종목의 통화 컬럼 하나에만 값을 넣는다 (5장)
CREATE TABLE portfolio_item_plan (
    product_id INT            NOT NULL PRIMARY KEY,     -- FK → product
    plan_krw   DECIMAL(12)    NULL,
    plan_usd   DECIMAL(12, 2) NULL,
    plan_eur   DECIMAL(12, 2) NULL,
    upd_date   DATETIME       NULL
);
```

### A.2 읽기만 하는 테이블
```sql
-- 환율. base_date가 PK, jpy_100은 100엔 기준
CREATE TABLE ex_rate (
    base_date DATETIME NOT NULL PRIMARY KEY,
    usd DECIMAL(12, 2) NULL, eur DECIMAL(12, 2) NULL, jpy_100 DECIMAL(12, 2) NULL
);

-- 종목 마스터 (통화 컬럼 없음 — 통화는 보유 건의 currency_unit)
CREATE TABLE product (
    product_id        INT AUTO_INCREMENT PRIMARY KEY,
    product_nm        VARCHAR(255) NOT NULL,
    symbol_alias      VARCHAR(255) NULL,                -- Ticker 또는 종목코드
    product_nm_detail VARCHAR(255) NULL,
    market            VARCHAR(20)  NOT NULL,            -- KOR, USA, DEU
    exchange          VARCHAR(50)  NULL,
    product_type_id   INT          NOT NULL,
    fin_inst_id       INT          NULL,
    comments          VARCHAR(255) NULL
);

-- 보유(주식·ETF). 같은 종목을 여러 계좌에 가질 수 있다 (UNIQUE product_id, acct_id, active_yn)
CREATE TABLE stock (
    s_id          INT AUTO_INCREMENT PRIMARY KEY,
    product_id    INT        NOT NULL,
    acct_id       INT        NOT NULL,
    active_yn     VARCHAR(1) DEFAULT 'Y' NOT NULL,      -- 'Y' = 현재 보유
    currency_unit VARCHAR(10) NOT NULL,                 -- KRW / USD / EUR / JPY
    quantity      INT NULL, av_quote DECIMAL(12, 2) NULL  -- 매입 관련 값은 이 API에서 쓰지 않는다
    /* ... */
);
-- 월별 평가액. (base_date, s_id) UNIQUE. base_date가 NULL일 수 있으니 MAX() 집계에 유의
CREATE TABLE stock_curr (
    curr_val_id INT AUTO_INCREMENT PRIMARY KEY,
    base_date   DATETIME NULL,
    s_id        INT NULL,                               -- FK → stock
    val_krw     DECIMAL(12)    DEFAULT 0 NOT NULL,      -- USD·EUR 보유 건에도 원화 환산액이 채워져 있다 (3.1)
    val_usd     DECIMAL(12, 2) DEFAULT 0.00 NULL,
    val_eur     DECIMAL(12, 2) DEFAULT 0.00 NULL,
    val_jpy     DECIMAL(12, 2) DEFAULT 0.00 NULL
);

-- 보유(예금·채권·펀드 등 비주식). 구조는 stock과 비슷하다
CREATE TABLE investment (
    invest_id     INT AUTO_INCREMENT PRIMARY KEY,
    product_id    INT        NOT NULL,
    acct_id       INT        NOT NULL,
    active_yn     VARCHAR(1) DEFAULT 'Y' NOT NULL,
    currency_unit VARCHAR(10) NOT NULL
    /* ... 개설일·만기일 등 */
);
CREATE TABLE investment_curr (
    curr_val_id INT AUTO_INCREMENT PRIMARY KEY,
    base_date   DATETIME NOT NULL,
    invest_id   INT NOT NULL,                           -- FK → investment
    val_krw     DECIMAL(12) NOT NULL,                   -- stock_curr와 동일하게 항상 원화 환산액
    val_usd     DECIMAL(12, 2) DEFAULT 0.00 NULL,
    val_eur     DECIMAL(12, 2) DEFAULT 0.00 NULL,
    val_jpy     DECIMAL(12, 2) DEFAULT 0.00 NULL
);

-- 대상(Portfolio 분류). scheme_id = 2 가 Portfolio 대상 (현재 7건)
CREATE TABLE scheme_asset_class (
    scheme_id INT NOT NULL, scheme_ac_id INT NOT NULL, name VARCHAR(100) NOT NULL, description VARCHAR(255),
    PRIMARY KEY (scheme_id, scheme_ac_id)
);
-- 세부분류 → 대상
CREATE TABLE scheme_sub_asset_class (
    scheme_id INT NOT NULL, sub_class_id INT NOT NULL, scheme_ac_id INT NOT NULL,
    PRIMARY KEY (scheme_id, sub_class_id)
);
-- 상품 유형 → 세부분류. scheme_id가 없으므로 대상은 scheme_sub_asset_class를 거쳐야 한다
CREATE TABLE product_type_mapping (
    product_type_id INT PRIMARY KEY, sub_class_id INT NOT NULL
);
```

### A.3 조인 경로 요약
보유 건 → 대상: `stock`(또는 `investment`) → `product.product_type_id` → `product_type_mapping.sub_class_id` → `scheme_sub_asset_class (scheme_id=2)` → `scheme_asset_class (scheme_id=2).scheme_ac_id`. 3.1의 참조 쿼리가 이 경로를 그대로 쓴다.
