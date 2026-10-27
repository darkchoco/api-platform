# PORT-BE-3 — 종목별 세부 현황 조회

- 상태: **완료** (커밋은 `git log`의 `PORT-BE-3` 항목 참고)
- 요구사항: [API-REQUIREMENTS-v3-portfolio.md](../API-REQUIREMENTS-v3-portfolio.md) 4장
- 이전: [PORT-BE-2](PORT-BE-2-execution-snapshot.md)

## 1. 구현

### 엔드포인트
`GET /api/portfolio/items[?schemeAcId=n]`

```json
[ { "productId": 19, "schemeAcId": 1, "productNm": "CJ제일제당", "currency": "KRW",
    "symbolAlias": "097950", "productNmDetail": null,
    "planKrw": null, "planUsd": null, "planEur": null,
    "currentKrw": 1866000, "currentUsd": 0.00, "currentEur": 0.00,
    "targetRatio": null } ]
```

### 동작
- **종목 행은 저장하지 않고 호출 시점마다 구성**한다: 최신 `base_date`의 활성(`active_yn='Y'`) 보유 건을 `product` 단위로 묶고, 같은 종목을 여러 계좌에 가졌다면 합산한다. 매도·청산된 종목은 자연히 빠진다.
- 대상 매핑과 통화별 합산 규칙은 PORT-BE-2와 같다(`current*`는 시가 기준, 해당 통화의 컬럼만). 대상에 매핑되지 않는 종목은 제외.
- **`currency`**: 보유 건의 `currency_unit`(`KRW|USD|EUR`). 한 종목이 둘 이상의 통화로 나오면 데이터 오류로 보고 **로그(WARN)만 남기고 첫 통화(알파벳순)를 사용**한다(500 아님).
- **투자계획금액**: `portfolio_item_plan`에서 가져오며 행이 없으면 셋 다 null. 값은 **종목 통화의 컬럼에서만** 읽고 나머지 둘은 null로 내린다.
- **정렬**: `schemeAcId`, `productNm` 오름차순(DB 콜레이션 기준).
- **`targetRatio`(대상내 비율, 톱다운)**: 종목 투자계획금액 ÷ 그 종목이 속한 대상의 'Portfolio 구성' 계획금액. 기준은 **최근 버전**(`version_id` 최대)이며 `fxUsd`도 그 버전에 저장된 값을 쓴다.

| 종목 통화 | 분자 | 분모 |
|---|---|---|
| KRW | `planKrw` | `krwUsdRatio × (버전.planKrw + 버전.planUsd × fxUsd)` |
| USD | `planUsd × fxUsd` | 위와 같음 |
| EUR | `planEur` | `eurRatio × 버전.planEur` |

  null이 되는 경우: 종목 계획 없음, 분모 0, KRW/USD 종목인데 `fxUsd`가 null, 최근 버전이 하나도 없음. 같은 대상 안 비율 합이 100% 미만이어도 오류가 아니다.

### 주요 파일
- `portfolio/controller/ItemController`
- `portfolio/service/ItemService`
- `portfolio/domain/` `TargetRatioCalculator`(순수 계산), `HoldingCurrency`
- `portfolio/repository/` `HoldingRepository`/`HoldingRow`(종목×통화 시가), `ItemPlanRepository`/`ItemPlan`, **`HoldingQueries`**(PORT-BE-2와 공유하는 SQL 조각)
- `portfolio/dto/ItemResponse`

## 2. 검증

- 전체 테스트 **73건 통과** (이 단계에서 신규 19건, 건너뛴 테스트 없음).
  - 단위: `TargetRatioCalculatorTest`(8) — 문서 예시 `8,000,000 ÷ 17,327,250 ≈ 0.4617` 재현, USD 환산, EUR 트랙(`fxUsd` 불필요), 계획 없음/분모 0/`fxUsd` 없음 → null, 비율이 1을 넘는 경우
  - 단위: `ItemServiceTest`(6) — 정렬 유지, 통화 컬럼만 읽기, 최근 버전 없음, 버전 detail에 없는 대상, 통화 혼합 종목, 빈 결과
  - dev DB 통합: `PortfolioItemApiTest`(5, 롤백) — 정렬·통화 집합·중복 없음, **대상별 `current*` 합계 = PORT-BE-2의 `exec*`** (정합성 교차 검증), `schemeAcId` 필터, 없는 대상 → 빈 목록 / 잘못된 값 → 400, **DB를 거친 `targetRatio`**(종목 계획 8,000,000 + 버전 계획 10,000,000, 비율 100% → 0.8)
- 실제 서버 확인(읽기 전용, 2026-10-27): 종목 **87건**(KRW 51 / USD 25 / EUR 11), 대상별 20·2·5·6·19·6·29건. dev DB에는 버전과 종목 계획이 0건이라 현재 `targetRatio`는 전부 null이다(정상). 통화 혼합 경고는 발생하지 않았다.
- 테스트가 dev DB에 데이터를 남기지 않음을 확인(versions 0건).

## 3. 문서에 없어서 정한 것 (확인 필요 항목 포함)

1. **JPY 보유 건은 종목 현황에서 제외**한다. 응답의 `currency`가 `KRW|USD|EUR`로 정의되어 있고 3.1도 JPY를 합산하지 않는다. 현재 최신월에 JPY 보유 건은 없다.
2. **`fxUsd`가 null이면 null이라는 규칙은 KRW/USD 종목에만 적용**했다. 문서 문장은 종목 통화와 무관하게 읽히지만, EUR 종목은 `fxUsd`를 쓰지 않으므로 EUR 비율까지 null로 만들 이유가 없다고 판단했다. 문서 그대로(모든 종목 null)를 원하면 `TargetRatioCalculator`의 분기 한 곳만 옮기면 된다.
3. **`targetRatio`는 소수 4자리 반올림(HALF_UP)**: 문서 예시(`0.4617`)와 맞췄다. 더 정밀한 값이 필요하면 `SCALE` 상수만 바꾸면 된다.
4. **최근 버전이 없으면 `targetRatio`만 null**이고 404가 아니다(목록은 정상 반환).
5. **없는 `schemeAcId`는 빈 배열**(200)이다. 문서에 규정이 없었다.
6. 통화 혼합 종목의 `current*`는 보유분 그대로 합산한다(`currency`만 첫 통화).
7. 종목 계획이 종목 통화와 다른 컬럼에 남아 있어도(통화가 바뀐 경우 등) 무시하고 통화 컬럼만 읽는다.

## 4. 이 단계에서 함께 바꾼 것 (PORT-BE-2 코드)

- 최신 기준월 활성 보유 건 / 대상 매핑 조인을 **`HoldingQueries`로 추출**해 `ExecutionRepository`와 `HoldingRepository`가 같은 SQL을 쓰도록 했다(핵심 규칙이 두 군데에 복붙되어 한쪽만 고쳐지는 것을 방지).
- `ExecutionRepository`의 합계에 `COALESCE(…, 0)` 추가.
- 위 변경 후에도 PORT-BE-2 테스트와 교차 검증 테스트가 모두 통과한다.

## 5. 후속 (PORT-BE-4)

- `PUT /api/portfolio/items/{productId}/plan`은 `ItemPlanRepository`에 upsert/delete를 추가하고, 응답은 `ItemService`에서 종목 1건을 같은 형태로 구성해 돌려주면 된다(`targetRatio` 재계산 포함).
- 종목의 통화는 `HoldingRepository`로 알 수 있다. 활성 보유 건이 없는 종목은 400.
