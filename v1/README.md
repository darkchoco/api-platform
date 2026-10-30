# api-platform

`apm-project`와 분리된 공용 Spring Boot API 플랫폼. apm-project 전용이 아니라 avdm 등 다른 프로젝트도 서비스할 수 있도록 Maven 멀티모듈로 구성한다.

## 기술 스택

- Java 25
- Spring Boot 4.1.1
- Maven 멀티모듈 (Maven Wrapper 포함 — 시스템 전역 Maven 설치 불필요, 로컬엔 JDK 25만 있으면 됨)

## 모듈

- `apm-api` — apm-project(자산/포트폴리오 관리 앱)를 위한 API
- 다른 모듈(예: `avdm-api`)은 추후 필요할 때 추가

## 개발 환경

**Windows 네이티브에서 개발한다(WSL 아님).** 로컬 실행 시 개발 DB(`localhost:43306`)에 연결하며, MySQL 자체는 이 저장소가 관리하지 않는 별도 서비스로 가정한다.

### 로컬 실행

```bash
DB_PASSWORD=<비밀번호> ./mvnw -pl apm-api -am spring-boot:run
```

기본 활성 프로파일은 `dev`이고, `DB_PASSWORD`는 기본값이 없어 반드시 지정해야 한다.

### 테스트

```bash
./mvnw -pl apm-api -am test
```

### 시간대

애플리케이션의 시간대는 설정값 **`app.timezone` 하나**(기본 `Europe/Berlin`, `application.yml`)로 정한다. 이 값을 두 곳이 함께 쓴다.

- `Clock` 빈(`ClockConfig`) — 월 판정(`v{YYMM}`)과 `ins_date`/`upd_date`/`fetchDate` 생성
- JDBC URL의 `serverTimezone` (`application-dev.yml`, `application-prd.yml`의 `${app.timezone}`)

바꿀 때는 `application.yml`의 값을 고치거나 `APP_TIMEZONE` 환경변수(또는 `--app.timezone=`)로 덮어쓴다. 잘못된 시간대 이름이면 기동에 실패한다. 단, `docker-compose.yml`은 `APP_TIMEZONE`을 컨테이너로 전달하지 않으므로 PRD에서 덮어쓰려면 `environment`에 추가해야 한다.

> 이미 저장된 `ins_date`/`upd_date` 등 DATETIME 값은 변환되지 않는다. 시간대를 바꾸면 그 시점부터 새로 쓰는 값만 새 시간대 기준이 된다.

## 배포

배포 대상은 **Debian WSL(PRD)** — Docker 컨테이너로 상시 구동한다. 이미지는 **PRD 호스트에서 직접 빌드**한다(Windows 개발 머신에는 Docker가 없고, PRD가 곧 배포 호스트라 별도 이미지 전달 과정이 필요 없다).

MySQL(`mysql8`)은 이 저장소가 관리하지 않는 별도 공유 인프라다(`cloudscape-personal-platform` 저장소가 관리, redmine/wordpress 등과 공유). apm-api는 그 인프라의 네트워크나 컨테이너에 대해 전혀 알지 못하고, 그냥 호스트에 이미 떠 있는 `localhost:${DB_PORT}`(prd 33306 / dev 43306)에 연결한다고만 가정한다.

### 최초 배포 (Debian PRD WSL)

```bash
# 1. 저장소 clone (최초 1회, private repo라 SSH 인증 필요)
git clone <repo-url> ~/lab/api-platform
cd ~/lab/api-platform

# 2. .env 생성 (최초 1회, 비밀값이라 git에 올라가지 않음)
cp .env.example .env
vi .env   # DB_PASSWORD만 채우면 충분 - 나머지는 prd 기본값 그대로 사용 가능

# 3. 빌드 + 기동
docker compose build
docker compose up -d
```

### 재배포

```bash
cd ~/lab/api-platform
git pull
docker compose build
docker compose up -d
```

### 검증

```bash
docker ps                                   # apm-api가 healthy인지 확인
curl http://localhost:38080/actuator/health
```

`docker-compose.yml`에 `restart: unless-stopped`가 설정되어 있어, Docker 데몬이 재시작되면 컨테이너도 자동으로 다시 기동된다.

## 컨벤션

- Spring 코드 컨벤션(로그/환경구성/패키지 규칙): [docs/spring-boot-conventions.md](docs/spring-boot-conventions.md)
- 이 프로젝트에서의 조정 사항, 커밋 규칙, 빌드 도구 등: [CLAUDE.md](CLAUDE.md)
