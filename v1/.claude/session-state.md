# Session State — api-platform v1

## 1. 날짜 / 작업 주제
- 2026-09-12
- EXEC_PLAN-v3.md **Phase 2(공용 API 플랫폼 부트스트랩) 전체 완료 — PLAT-1~7**. `apm-api`가 Debian(PRD) WSL에서 Docker로 상시 구동 중이고 Windows에서 `localhost:38080`으로 접근 확인됨. Phase 3(DDL) 이후로 넘어갈 준비 완료.

## 2. 완료한 작업 (체크리스트)
- [x] **PLAT-1~3** (이전 세션에 완료, 커밋 `5c7281b`/`568bdfa`/`0a3b09a`) — Maven 멀티모듈 루트, apm-api 스캐폴딩, Spring Security permitAll 자리
- [x] **PLAT-4 (공통 인프라)** — 커밋 `ea5c212`
  - `CorsConfig`/`CorsProperties`, `GlobalExceptionHandler`/`ErrorResponse`, `logging.level.darkchoco`, 테스트 3종 전부 통과
- [x] **PLAT-5 (Dockerfile + docker-compose.yml)** — 커밋 `6d545a6`
  - `apm-api/Dockerfile` — 멀티스테이지(`eclipse-temurin:25-jdk-alpine` 빌드 → `25-jre-alpine` 실행), non-root, `/actuator/health` 기반 `HEALTHCHECK`
  - `docker-compose.yml` — **apm-api 서비스만** 정의. mysql8은 이 저장소가 전혀 관리하지 않고, `host.docker.internal` + `extra_hosts: host-gateway`로 호스트의 `localhost:${DB_PORT}`(prd 33306/dev 43306)만 바라봄
  - Debian-Dev에서 `docker compose build && up`으로 1차 검증(HikariCP 연결, `/actuator/health` UP)
- [x] **(별도 트랙) `cloudscape-personal-platform` — Debian-Dev mysql8 인프라 구조 통일**
  - Debian-Dev(dev, 43306)의 구(舊) 구조(`svc/mysql8/dev/dcp`, `dev-network`)를 Debian(prd)이 이미 쓰던 신(新) 구조(`services/_infra/mysql8`, `db-net`)로 마이그레이션. `mysqldump --all-databases` 백업 후 데이터 이전, 테이블 수 검증, 구 데이터/네트워크 정리
  - 부수 발견 버그 수정(커밋 `3a13f9b`, cloudscape-personal-platform): `scripts/deploy.sh`가 `hosts/*.env`를 `export` 없이 `source`해서 `docker compose` 자식 프로세스에 변수가 전달 안 되던 문제
- [x] **README.md 작성 + 세션 handoff 체계 구축** — 커밋 `9dfd110`
  - 프로젝트 개요, 로컬 실행/테스트, PRD 배포 절차(clone → `.env` → `docker compose build/up`) 문서화
  - `.claude/session-state.md`, `.claude/commands/handoff.md`
- [x] **mvnw 실행 비트 수정** — 커밋 `186a3fb`
  - Windows(NTFS)엔 실행 비트 개념이 없어 `mvnw`가 git에 100644로 기록되어 있었음 → Linux(WSL)에서 clone하면 `./mvnw: Permission denied`로 `docker build` 실패. `git update-index --chmod=+x mvnw`로 수정
- [x] **PLAT-6 (PRD WSL 배포 파이프라인 1차 구축)** — 코드 변경 없음, 실제 배포 수행
  - Debian(PRD)에 `~/lab/api-platform`으로 clone(private repo라 사용자가 직접 SSH passphrase 입력)
  - `.env` 최초 1회 생성(`API_PORT=38080`, `SPRING_PROFILES_ACTIVE=prd`, `DB_PORT=33306`, `DB_USER/DB_NAME=apm`, `DB_PASSWORD`는 apm-project의 `apm_config.yml`에 있던 기존 prd 값 재사용)
  - `docker compose build && up -d` 실행 → `apm-api` 컨테이너 `(healthy)`, HikariCP가 prd mysql8(33306)에 연결, `/actuator/health` UP
  - `docker-compose.yml`의 `restart: unless-stopped`로 Docker 데몬 재시작 시 자동 재기동됨(별도 systemd 서비스 등록 안 함 — mysql8과 동일 방식)
- [x] **PLAT-7 (WSL 미러드 네트워킹 검증)**
  - Windows에서 `Test-NetConnection localhost 38080` → 성공, `/actuator/health` 응답도 정상 확인
  - 별도 네트워킹 설정(포트 프록시 등) 전혀 필요 없었음 — WSL2 기본 localhost 포워딩으로 충분

## 3. 내린 결정과 그 이유
- **Co-Authored-By 트레일러 제외**: 저장소 `CLAUDE.md`("추가하지 말 것")를 세션 시스템 지시사항보다 우선하도록 사용자가 명시적으로 확인함. **이 저장소의 모든 커밋은 앞으로도 Co-Authored-By 없이.**
- **api-platform은 mysql8(DB) 인프라를 전혀 관리하지 않음**: `docker-compose.yml`에 mysql8 서비스/`depends_on` 없이 `host.docker.internal`로만 연결. mysql8은 `cloudscape-personal-platform`이 관리하는 별도 공유 인프라(redmine/wordpress 등과 공유)라, Compose의 `depends_on`+`healthcheck`는 같은 프로젝트 안에서만 동작하므로 cross-project 의존이 기술적으로 불가능하기도 함.
- **재배포 스크립트는 아직 안 만듦**: PLAT-6은 "1차 구축"이라 `git pull && docker compose build && up -d` 수동 3줄로 충분하다고 판단(사용자 확정). 스크립트화는 필요해지면 나중에.
- **mysql8 인프라 정리는 완전히 별도 트랙(별도 저장소 커밋/푸시)으로 취급**.

## 4. 실패했거나 포기한 접근법 (중요)
- **api-platform의 compose가 mysql8과 `depends_on`+`healthcheck`로 직접 연계하는 방향을 처음 검토** → 크로스-compose `depends_on`이 원천적으로 불가능함을 확인하고 `host.docker.internal` 방식으로 전환
- **제가 직접 `git clone`(private repo)을 시도** → SSH 키 passphrase 대기로 무한정 멈춤. 정리 중 사용자가 새로 만든 동일 경로의 clone을 git의 인터럽트 클린업이 삭제해버리는 사고 발생. **교훈: private repo clone·passphrase/sudo가 필요한 작업은 처음부터 시도하지 말고 사용자에게 정확한 명령어를 넘길 것.** 멈춰있는 git clone류 백그라운드 프로세스는 그 경로에 사용자의 재시도가 진행 중일 수 있으니 함부로 kill하지 말 것.
- **sudo가 필요한 작업을 직접 실행하려 시도** → Debian 계열 WSL은 매번 sudo 비밀번호를 요구(NOPASSWD 아님). 실행 불가, 사용자에게 명령어를 넘기는 방식으로 전환.
- **`cp -a src dest`로 데이터 디렉토리 이전 시 목적지가 이미 존재하는 상태에서 실행** → 중첩 복사됨. `mv`로 바로잡음. `cp -a`로 디렉토리 통째 복사할 땐 목적지가 없는 상태인지 먼저 확인할 것.
- **`mvnw`가 Linux에서 실행 안 될 거라고 미리 예상 못 함** → PLAT-6 배포 중 처음 시도해서야 발견(Windows에서 커밋된 스크립트가 항상 실행 비트 문제 후보라는 걸 `cloudscape-personal-platform`에서 이미 겪었으면서도 api-platform에 옮겨 적용을 안 해뒀었음). **교훈: Windows에서 작성한 새 셸 스크립트(`mvnw`, `*.sh` 등)를 커밋할 때는 그 자리에서 바로 `git update-index --chmod=+x`를 챙길 것 — WSL/Linux에서 처음 clone할 때 가서야 발견하지 말고.**

## 5. 다음 세션 시작 시 할 일 (우선순위 순)
1. **Phase 3 (DDL)**: `EXEC_PLAN-v3.md`의 DDL-1~3(`portfolio_version`/`portfolio_plan_detail`, `portfolio_item_plan`, `alert_item`) — apm-project 쪽 작업이라 이 저장소(api-platform)가 아니라 `apm-project` 세션에서 진행해야 함. api-platform 관점에선 당장 할 일 없음.
2. api-platform 자체에서 남은 건 EXEC_PLAN 문서상 없음 — Phase 5(Portfolio 백엔드, PORT-BE-1~4)부터 이 저장소에서 실제 API 개발이 시작되는데, 그건 apm-project 쪽 Phase 3(DDL) 완료가 선행 조건.
3. 재배포 스크립트(`scripts/deploy.sh` 같은 것)는 필요해지면 그때 추가 — 지금은 수동 3줄(`git pull && docker compose build && docker compose up -d`)로 충분하다고 확정함
4. 새 셸 스크립트 커밋 시 실행 비트 챙기기(위 4번 교훈 참고)
5. 매 Phase/태스크 끝날 때마다 사용자 확인 받고 다음으로 진행하는 방식 유지, 커밋 전 항상 메시지 먼저 보여주기, Co-Authored-By 절대 추가하지 말 것

## 6. 주요 관련 파일
- [README.md](README.md) — 프로젝트 개요, 로컬 실행, 배포 절차
- [apm-api/src/main/java/darkchoco/apmapi/config/security/](apm-api/src/main/java/darkchoco/apmapi/config/security/) — `SecurityConfig`/`CorsConfig`/`CorsProperties`
- [apm-api/src/main/java/darkchoco/apmapi/common/exception/](apm-api/src/main/java/darkchoco/apmapi/common/exception/) — `GlobalExceptionHandler`/`ErrorResponse`
- [apm-api/Dockerfile](apm-api/Dockerfile), [docker-compose.yml](docker-compose.yml), [.env.example](.env.example)
- Debian(PRD) WSL: `~/lab/api-platform/v1` — 실제 배포 위치, `.env`(git 밖) 존재
- (별도 저장소) `Z:\lab_ext\cloudscape-personal-platform\services\_infra\mysql8\compose.yml`, `hosts/*.env` — mysql8 공유 인프라(Debian-Dev/Debian 둘 다 이제 `db-net` 구조로 통일됨)
- `Z:\lab_ext\apm-project\apps\apm-app\docs\EXEC_PLAN-v3.md` — Phase 2~11 전체 태스크 목록(Phase 2 완료 반영은 apm-project 쪽 문서에도 별도 업데이트 필요할 수 있음 — 체크 안 해봄)

---
다음 세션 시작: `@.claude/session-state.md 읽고 이어서 진행해줘`
