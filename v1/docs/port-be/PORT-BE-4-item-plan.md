# PORT-BE-4 — 종목 투자계획금액 저장

- 상태: **완료** (커밋은 `git log`의 `PORT-BE-4` 항목 참고)
- 요구사항: [API-REQUIREMENTS-v3-portfolio.md](../API-REQUIREMENTS-v3-portfolio.md) 5장
- 이전: [PORT-BE-3](PORT-BE-3-items.md)

## 1. 구현

### 엔드포인트
`PUT /api/portfolio/items/{productId}/plan`

```json
{ "planAmount": 8000000 }
```

응답은 갱신된 **종목 1건**(PORT-BE-3의 항목 형태)이며 `targetRatio`가 재계산되어 있다. 인라인 편집 직후 화면이 새 비율을 바로 보여주기 위함이다.

### 동작
- **통화는 서버가 정한다**: 종목의 보유 통화(`currency`)에 해당하는 컬럼(`KRW`→`plan_krw`, `USD`→`plan_usd`, `EUR`→`plan_eur`)에만 저장하고 나머지 두 컬럼은 **NULL로 만든다**. 다른 통화 컬럼에 값이 남아 있던 행도 이때 정리된다.
- **upsert**: `portfolio_item_plan`(`product_id` PK)에 `INSERT ... ON DUPLICATE KEY UPDATE`, `upd_date` = 서버 시각(Asia/Seoul, 초 단위). 버전 이력은 두지 않고 최신값만 유지한다. MySQL 8.0.20+에서 deprecated인 `VALUES()` 대신 같은 이름 있는 파라미터를 재사용했다.
- **`planAmount`가 null이면 "계획 없음"** — 행을 삭제한다. 행이 없어도 오류가 아니다(멱등).
- **검증 순서와 상태코드**
  1. `productId`가 `product`에 없으면 **404**
  2. 현재 보유 중인 Portfolio 종목이 아니면 **400** (통화를 알 수 없음)
  3. `planAmount` 검증 위반이면 **400** — 음수 불가, **원화는 정수만**, 달러·유로는 소수 2자리까지, 컬럼 범위(`DECIMAL(12)` / `DECIMAL(12,2)`) 초과 불가
  4. 본문 파싱 오류, `productId` 형식 오류도 400
- 검증에 실패한 요청은 아무것도 저장하지 않는다. 쓰기는 `@Transactional`이다.

### 주요 파일
- `portfolio/controller/ItemController` — `PUT` 추가
- `portfolio/service/ItemService.updatePlan` — 저장 후 `buildItems`로 갱신된 1건을 같은 경로로 구성(PORT-BE-3과 응답 로직 공유)
- `portfolio/repository/` `ItemPlanRepository`(`upsert`/`delete`), `ProductRepository`(존재 확인), `HoldingRepository.findHoldingsOfProduct`
- `portfolio/domain/AmountRules` — 금액 검증 규칙(아래 4번 참고)
- `portfolio/dto/ItemPlanRequest`

## 2. 검증

- 전체 테스트 **92건 통과** (이 단계에서 신규 19건, 건너뛴 테스트 없음).
  - 단위: `AmountRulesTest`(4), `ItemServiceTest`(+8, 합계 14) — 원화 저장과 비율 재계산, 달러 소수 2자리, null 삭제, 없는 종목 404, 미보유 종목 400(쓰기 없음), 통화에 맞지 않는 금액 400(쓰기 없음), `8000000.00` 허용, 통화 혼합 종목의 첫 통화 처리
  - dev DB 통합(롤백): `PortfolioItemPlanApiTest`(7) — 원화/달러/유로가 각자 통화 컬럼에만 저장, **다른 컬럼에 남은 값(KRW 999, EUR 77.00)이 NULL로 정리됨**, 재저장 시 행이 늘지 않고 덮어씀, `upd_date`가 고정 시계 값, null 삭제와 반복 삭제, 저장 직후 목록 조회에 반영, 비율 재계산(`8,000,000 ÷ 10,000,000 = 0.8`, 버전 저장 후), 오류 케이스(404/400들)와 실패 시 미저장, 존재하지만 보유 중이 아닌 종목 400
- 실제 서버 확인(2026-10-27): 존재하지 않는 종목 404, 음수 400, 잘못된 본문 400. 거절된 요청 뒤에도 계획이 있는 종목은 0건. **dev DB에는 실제 쓰기를 하지 않았다**(쓰기는 롤백되는 통합 테스트로만 검증).

## 3. 문서에 없어서 정한 것

1. **"Portfolio 종목 목록에 없는 종목"은 모두 400**: 활성 보유 건이 없는 종목뿐 아니라, 보유는 하지만 대상에 매핑되지 않은 종목(연금, 보험 등)과 JPY만 보유한 종목도 같다. 응답 형태가 `schemeAcId`와 `currency`를 요구하는데 이런 종목은 목록(PORT-BE-3)에도 나오지 않기 때문이다.
2. **`{}`처럼 `planAmount`가 아예 없는 본문도 null로 취급해 삭제**한다(JSON 상 null과 구분하지 않음). 의도치 않은 삭제를 막으려면 필드 존재 여부를 따로 구분해야 하는데 문서가 그렇게 요구하지 않아 단순하게 두었다.
3. **검증 순서는 404 → 400**. 존재하지 않는 종목에 음수를 보내면 404가 우선한다.
4. 통화 혼합 종목(데이터 오류)은 PORT-BE-3과 같이 첫 통화(알파벳순)를 따른다.

## 4. 이 단계에서 함께 바꾼 것 (PORT-BE-1 코드)

- `VersionSaveValidator`에 있던 금액 검증(원화/외화 범위·자릿수)을 **`AmountRules`로 추출**해 버전 저장과 종목 계획 저장이 같은 규칙을 쓴다. 기존 동작과 메시지는 그대로이고(PORT-BE-1 테스트 전부 통과), 원화 정수 검사(`정수만 입력할 수 있습니다`)만 `BigDecimal` 입력을 위해 추가되었다.

## 5. Phase 5(Portfolio 백엔드) 마무리 메모

PORT-BE-1~4가 모두 끝났다. 남은 것은 apm-project 쪽이다 (요구사항 문서 6장 I, J 참고).
- `serve`가 `/api/*` 전체를 38080으로 보내는데 Spring에는 `months/dashboard/report/search`가 아직 없다 — 라우팅 분리 또는 MIGRATE 선행 결정 필요.
- 프론트 `useApi`는 GET 전용이라 POST/PUT 헬퍼가 필요하다(PORT-FE 작업).
- 프론트의 `itemToTargetRatio`는 서버 계산(`targetRatio`)으로 대체되므로 제거 대상이다.
