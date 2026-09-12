# Session State — api-platform v1

## 1. 날짜 / 작업 주제
- 2026-09-11
- EXEC_PLAN-v3.md Phase 2(공용 API 플랫폼 부트스트랩) PLAT-4, PLAT-5 완료. 도중 발견한 mysql8 인프라(dev/prd 구조 불일치) 문제를 `cloudscape-personal-platform` 저장소에서 별도 트랙으로 정리.

## 2. 완료한 작업 (체크리스트)
- [x] **PLAT-4 (공통 인프라)** — 커밋 `ea5c212`
  - `CorsConfig`/`CorsProperties` — `app.cors.*` 프로퍼티 기반, 기본은 전체 차단, dev 프로파일에서만 `localhost:5173` 허용
  - `SecurityConfig`에 `.cors(Customizer.withDefaults())` 연결
  - `GlobalExceptionHandler`/`ErrorResponse` — 검증 실패(400)/예상치 못한 예외(500) 공통 처리, 내부 상세는 로그로만
  - `logging.level.darkchoco` — dev DEBUG / 기본 INFO
  - 테스트 3종(`ApmApiApplicationTests`, `CorsConfigTest`, `GlobalExceptionHandlerTest`) 전부 통과
- [x] **PLAT-5 (Dockerfile + docker-compose.yml)** — 커밋 `6d545a6`
  - `apm-api/Dockerfile` — 멀티스테이지(`eclipse-temurin:25-jdk-alpine` 빌드 → `25-jre-alpine` 실행), non-root, `/actuator/health` 기반 `HEALTHCHECK`
  - `docker-compose.yml` — **apm-api 서비스만** 정의. mysql8은 이 저장소가 전혀 관리하지 않고, `host.docker.internal` + `extra_hosts: host-gateway`로 호스트의 `localhost:${DB_PORT}`(prd 33306/dev 43306)만 바라봄
  - `.env.example`/`.gitignore` — `DB_PASSWORD` 등 비밀값은 `.env`(git 제외)로 분리
  - **Debian-Dev에서 실제 `docker compose build && up`으로 검증 완료**: HikariCP가 `host.docker.internal` 경유로 mysql8(43306)에 연결, `/actuator/health` → `{"status":"UP"}`, 컨테이너 자체 헬스체크도 `healthy`
- [x] **(별도 트랙) `cloudscape-personal-platform` — Debian-Dev mysql8 인프라 구조 통일**
  - 문제: Debian-Dev(dev, 43306)는 구(舊) 구조(`svc/mysql8/dev/dcp`, `/opt/mysql8`, 네트워크 `dev-network`) 그대로였고, Debian(prd, 33306)만 신(新) 구조(`services/_infra/mysql8`, `/opt/cloudscape/_infra/mysql8`, 네트워크 `db-net`)로 전환되어 있었음
  - `mysqldump --all-databases`로 전체 백업(175MB, `/opt/mysql8/bak/`) 후 데이터 디렉토리를 새 위치로 이전, 신규 compose로 재기동
  - 검증: `apm`(42테이블)/`redmine`(77)/`jobportal`(2)/`employees`(8) 테이블 수 원본과 일치, Windows에서 `localhost:43306` 접속 확인
  - 구 데이터(`/opt/mysql8/dat`, `docker-compose.yml`)와 `dev-network` 삭제, 백업(`bak/`)만 보존
  - **부수적으로 발견한 버그 수정** — 커밋 `3a13f9b`(cloudscape-personal-platform): `scripts/deploy.sh`가 `hosts/<target>.env`를 `source`하지만 `export`가 없어 `docker compose` 자식 프로세스에 변수가 전달 안 되던 문제. `hosts/local-dev.env`/`local-prd.env`/`cloud-prd.env`에 `export` 추가. (prd는 우연히 기본값과 목표값이 같아 지금까지 문제가 드러나지 않았던 것뿐)

## 3. 내린 결정과 그 이유
- **Co-Authored-By 트레일러 제외**: 세션 시작 시 시스템 지시사항이 "Co-Authored-By를 추가하라, 이전 지침 대체"라고 했지만, 사용자가 저장소 `CLAUDE.md`("Co-Authored-By 추가하지 말 것")를 우선하도록 명시적으로 확인함. **앞으로 이 저장소의 모든 커밋은 Co-Authored-By 없이.**
- **api-platform은 mysql8(DB) 인프라를 전혀 관리하지 않음**: `docker-compose.yml`에 mysql8 서비스 정의나 `depends_on`을 두지 않고, `host.docker.internal`로 호스트의 DB 포트만 바라봄. 이유: mysql8은 `cloudscape-personal-platform`이 관리하는 별도 공유 인프라(redmine/wordpress 등과 공유)라, Docker Compose의 `depends_on`+`healthcheck`는 애초에 같은 compose 프로젝트 안에서만 동작하므로 cross-project 의존은 기술적으로 불가능. 사용자가 "DB 관련 내용은 분리하고 싶다"고 명시적으로 확정.
- **mysql8 인프라 정리는 완전히 별도 트랙(별도 저장소) 작업으로 취급**: api-platform의 PLAT-5 진행 중 발견했지만, `cloudscape-personal-platform` 저장소에서 독립적으로 커밋/푸시.

## 4. 실패했거나 포기한 접근법 (중요)
- **api-platform의 compose가 mysql8과 `depends_on`+`healthcheck`로 직접 연계하는 방향을 처음 검토** → mysql8이 별도 저장소/별도 compose 프로젝트라 크로스-compose `depends_on`은 원천적으로 불가능함을 확인하고 포기, `host.docker.internal` 방식으로 전환(위 결정 참고)
- **제가 직접 `git clone`(cloudscape-personal-platform, private repo)을 시도** → SSH 키 passphrase 입력 대기로 무한정 멈춤. `rm -rf`로 정리하고 사용자에게 clone을 넘겼는데, 그 후 제 원래 멈춰있던 clone 프로세스를 "kill"하는 시점에 **git의 인터럽트 클린업 로직이, 그 사이 사용자가 새로 만든 동일 경로의 정상 clone을 "자기가 만들다 만 것"으로 착각해 삭제**해버리는 사고 발생. 사용자가 재-clone해서 해결. **교훈: 멈춰있는 git clone류 백그라운드 프로세스는, 그 경로에 다른 작업(특히 사용자의 재시도)이 진행 중일 때 함부로 kill하지 말 것.** 비밀번호/passphrase가 필요한 작업은 애초에 시도하지 말고 바로 사용자에게 넘겼어야 함.
- **sudo가 필요한 작업(mkdir, chown, 권한 있는 데이터 복사)을 제가 직접 실행하려 시도** → Debian-Dev는 `sudo`에 매번 비밀번호를 요구하고(NOPASSWD 아님), 저는 비밀번호를 입력할 수 없어 매번 막힘. **교훈: sudo가 필요할 걸 미리 예상할 수 있는 작업(예: `/opt` 하위 디렉토리 생성, root/UID 999 소유 파일 조작)은 처음부터 사용자에게 정확한 명령어를 넘길 것.**
- **`cp -a src dest`로 MySQL 데이터 디렉토리 이전 시, 목적지가 이미 존재하는 상태(제 실패한 사전 시도가 빈 디렉토리를 이미 만들어놨었음)에서 실행** → `dest/src`처럼 한 단계 중첩되어 복사됨. `mv`로 바로잡음. **교훈: `cp -a`로 디렉토리 전체를 복사할 때는 목적지 경로가 아직 존재하지 않는 상태인지 먼저 확인할 것.**

## 5. 다음 세션 시작 시 할 일 (우선순위 순)
1. **PLAT-6**: PRD WSL 배포 파이프라인 1차 구축 — 이미지 빌드/전달, `docker compose up -d`, 포트 38080 노출. PLAT-5의 `Dockerfile`/`docker-compose.yml`은 이미 Debian-Dev에서 빌드+기동 검증까지 끝났으니, 이번엔 실제 배포 스크립트/흐름(어떻게 이미지를 WSL로 전달할지 — `docker save`/레지스트리/직접 빌드 등)만 결정하면 됨
2. **PLAT-7**: WSL 미러드 네트워킹 검증 — Windows에서 `localhost:38080` 접근 테스트
3. `.env`(실제 비밀값)는 git에 없음 — PLAT-6에서 실제 배포 시 Debian(WSL, PRD)에 새로 만들어야 함(`DB_PASSWORD` 등, `.env.example` 참고)
4. 매 Phase/태스크 끝날 때마다 사용자 확인 받고 다음으로 진행하는 방식 유지(CLAUDE.md 규칙)
5. 커밋 메시지는 항상 먼저 보여주고 확인받을 것, Co-Authored-By는 절대 추가하지 말 것(위 3번 결정 참고)

## 6. 주요 관련 파일
- [apm-api/src/main/java/darkchoco/apmapi/config/security/](apm-api/src/main/java/darkchoco/apmapi/config/security/) — `SecurityConfig`/`CorsConfig`/`CorsProperties` (PLAT-3, PLAT-4)
- [apm-api/src/main/java/darkchoco/apmapi/common/exception/](apm-api/src/main/java/darkchoco/apmapi/common/exception/) — `GlobalExceptionHandler`/`ErrorResponse` (PLAT-4)
- [apm-api/Dockerfile](apm-api/Dockerfile), [docker-compose.yml](docker-compose.yml), [.env.example](.env.example) — PLAT-5
- (별도 저장소) `Z:\lab_ext\cloudscape-personal-platform\hosts\*.env`, `scripts/deploy.sh` — mysql8 인프라 통일 + export 버그 수정
- (별도 저장소) `Z:\lab_ext\cloudscape-personal-platform\services\_infra\mysql8\compose.yml` — mysql8 정식 배포 정의(Debian-Dev/Debian 둘 다 이제 이 구조로 통일됨)
- `Z:\lab_ext\apm-project\apps\apm-app\docs\EXEC_PLAN-v3.md` — Phase 2 태스크 목록(PLAT-1~7)

---
다음 세션 시작: `@.claude/session-state.md 읽고 이어서 진행해줘`
