# ALERT-BE-1 — 만기일(MATURITY) 알림 계산

- 상태: **완료** (커밋은 `git log`의 `ALERT-BE-1` 항목 참고)
- 요구사항: [API-REQUIREMENTS-v3-alert.md](../API-REQUIREMENTS-v3-alert.md) 2장, 3장
- 다음: ALERT-BE-3(조회/닫기/새로고침 API)

## 1. 구현

이 단계에는 API가 없다. 만기 알림을 `alert_item`에 계산해 두는 서비스와 기동 시 1회 실행만 만들었다. 조회·닫기·`POST /api/alerts/refresh`와 하루 1회 지연 재계산(결정 A)은 ALERT-BE-3에서 붙인다.

### 동작
- **대상**: `investment` 중 `active_yn='Y'` AND `last_due_date IS NOT NULL`(결정 B, `close_date`는 보지 않음). 제목용으로 `product`, `acct` → `fin_inst`를 조인한다(`fin_inst`는 LEFT JOIN).
- **단계**: `d = 만기일(날짜) − 오늘`(오늘 = `LocalDate.now(clock)`, `app.timezone`). `d < 0` OVERDUE, `0~14` URGENT, `15~30` MILD, `> 30` 알림 없음. 아주 오래전 만기도 그대로 OVERDUE(결정 C).
- **재계산(`MaturityAlertService.refresh`, 트랜잭션 하나, 멱등)**
  1. 대상마다 `(MATURITY, invest_id, 현재 단계)`를 upsert — 없으면 `OPEN`·`dismissed_yn='N'`으로 insert, 있으면 `status='OPEN'`, `title`, `detail_url`, `due_date`, `upd_date`만 갱신(**`dismissed_yn`은 유지**)
  2. 그 밖의 **열린 MATURITY 행은 모두 `CLOSE`** — 단계가 바뀐 건의 이전 단계 행(MILD → URGENT), 보유 종료·만기일 삭제·30일 초과가 된 건을 한 번에 처리한다. `dismissed_yn`은 건드리지 않는다.
  3. 결과로 `RefreshCount(open, closed)`를 돌려준다(BE-3의 새로고침 응답에 쓴다).
- 값: `title = "{product_nm} · {fin_inst_nm}"`, `detail_url = NULL`, `due_date = last_due_date`, `upd_date = 서버 시각(초 단위)`.
- **기동 시 1회**: `AlertStartupRefresher`가 `ApplicationReadyEvent`에서 실행. 실패하면 ERROR 로그만 남기고 기동은 계속한다. 설정 `app.alert.refresh-on-startup`(기본 `true`)으로 켜고 끈다.

### 주요 파일
- `alert/domain/` `AlertTier`(단계 + 만기 단계 계산, 선언 순서 = 심각도), `AlertSourceType`, `RefreshCount`
- `alert/repository/` `MaturityTargetRepository`·`MaturityTarget`(대상 조회, 제목 구성), `AlertItemRepository`(`upsertOpen`/`findOpen`/`close`)·`OpenAlert`
- `alert/service/` `MaturityAlertService`, `AlertStartupRefresher`
- `application.yml` — `app.alert.refresh-on-startup: true`
- `src/test/resources/config/application.yml` — 테스트에서는 `false`

## 2. 검증

- 전체 테스트 **117건 통과** (이 단계에서 신규 16건, 건너뛴 테스트 없음).
  - 단위: `AlertTierTest`(8) — 경계값 -1/0/14/15/30/31, `MaturityTargetTest`(2) — 제목 구성, 금융기관명이 없을 때
  - dev DB 통합(롤백): `MaturityAlertRefreshTest`(6) — investment는 바꾸지 않고 만기일이 있는 투자건 하나를 골라 **시계를 그 만기일 기준으로 옮겨 가며** 확인. insert 값(제목·만기일·`upd_date`), MILD → URGENT → OVERDUE escalation 시 이전 행 CLOSE + 새 행 OPEN, 닫은 알림이 재계산 후에도 닫힌 채 유지되고 조건에서 벗어나면 CLOSE(`dismissed_yn` 유지), 31일 이상이면 행 없음, 같은 날 두 번 실행해도 결과 동일, 테스트에서 기동 시 재계산 빈이 없음
- 요구사항 3.5 예시 대조(임시 테스트, 확인 후 삭제): 시계를 2026-11-04로 두고 재계산하면 대상 11건 중 **OVERDUE 3건(10, 27, 127) + MILD 1건(125), 총 4건**으로 문서와 일치.
- **dev DB에는 실제 쓰기를 하지 않았다**(롤백되는 테스트로만 검증). 서버를 실제로 띄우면 기동 시 재계산이 dev `alert_item`에 위 4건을 실제로 만든다.

## 3. 문서에 없어서 정한 것

1. **테스트에서는 기동 시 재계산을 끈다.** `ApplicationReadyEvent`의 계산은 테스트의 `@Transactional` 롤백 밖에서 돌아 dev DB를 실제로 바꾸기 때문이다. `src/test/resources/config/application.yml`은 main의 `application.yml`을 대체하지 않고 그 위에 덮어써지므로 테스트 클래스마다 설정할 필요가 없다.
2. **`fin_inst_nm`이 없으면 제목은 상품명만** 쓴다(`fin_inst_nm`이 NULL 허용).
3. **제목이 255자를 넘으면 잘라서 저장**한다(`title VARCHAR(255)`, 상품명만으로도 255자까지 가능).
4. **CLOSE로 바꿀 때도 `upd_date`를 갱신**한다(컬럼 설명이 "마지막 갱신(재계산/재fetch) 시각"이므로).
5. 기동 시 재계산 실패는 **ERROR** 로그(기동은 계속). Jira/Todoist 실패(WARN)와 달리 우리 DB 문제라 더 심각하게 본다.
