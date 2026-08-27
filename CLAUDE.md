# CLAUDE.md

이 저장소(`api-platform`)는 apm-project와 분리된 **공용 Spring Boot API 플랫폼**이다.
apm-project 전용이 아니라 avdm 등 다른 프로젝트도 서비스할 수 있도록 멀티모듈로 구성한다.

# Spring 개발 컨벤션

로그 규칙 · 환경 구성 · 패키지/네이밍 규칙 등은 [docs/spring-boot-conventions.md](docs/spring-boot-conventions.md)를 따른다.
Spring 코드는 약간의 효율성을 희생하더라도 **가독성을 우선**한다 — 직접 유지보수할 코드이므로 잘 정리되고 읽기 쉬운 코드가 우선이다.

## 이 프로젝트에서의 조정 사항 (spring-boot-conventions.md 대비)

- **패키지 구조 간소화**: 가이드 문서의 `com.example.<앱이름>` 형태 대신,
  `src/main/java/darkchoco/<프로젝트명>`으로 짧게 간다.
  예: `apm-api` 모듈 → `darkchoco.apmapi`
- **최신 기능/스타터 우선 사용**: 가능하면 Spring Boot 4의 최신 기능·명시적 스타터를 사용한다.
  예시로 언급된 것: `spring-boot-starter-web`(구식/모호한 이름) 대신 `spring-boot-starter-webmvc`처럼
  더 명시적인 최신 아티팩트를 사용. ⚠️ 실제 아티팩트명은 PLAT-2에서 의존성 추가 시
  Spring Boot 4.1.x 공식 문서로 재확인할 것(사용자가 예시로 든 것이지 검증된 사실은 아님)

# 빌드

- **Maven** 멀티모듈(Gradle 아님 — 초기에 Gradle로 검토했으나 최종적으로 Maven 확정)
- 루트 `pom.xml`(`<packaging>pom</packaging>`) + 모듈별 `pom.xml`
- Java 25, Spring Boot 4.1.1(또는 그 이상 최신 안정 버전 — 작업 시작 시 https://spring.io/blog 에서 재확인)
- Maven Wrapper(`mvnw`/`mvnw.cmd`) 사용, 시스템 전역 Maven 설치에 의존하지 않음

# 모듈

- `apm-api` — apm-project(자산/포트폴리오 관리 앱)를 위한 API
- 다른 모듈(예: avdm-api)은 추후 필요할 때 추가. 지금은 apm-api만 스캐폴딩

# 개발 환경

- **Windows 네이티브**에서 개발(WSL 아님). 배포는 Docker 컨테이너로 빌드해 Debian WSL(PRD)에 올림
- 참고: [scripts/db/CLAUDE.md](../../apm-project/scripts/db/CLAUDE.md) — apm DB 스키마(DDL/DML) 단일 소스,
  개발 DB는 `localhost:43306`, 운영은 `localhost:33306`

# 참고 문서

- 자세한 실행계획은 apm-project 저장소의 `apps/apm-app/docs/EXEC_PLAN-v3.md`(Phase 2, PLAT-1~7 태스크)와
  `apps/apm-app/docs/PRD-v3-open-questions.md`(Portfolio DB 스키마, 배포 아키텍처 결정 근거) 참고
- 작업 진행 시 apm-project와 동일하게: **Phase/태스크 단위로 순차 진행하고, 각 단계 완료 후 다음으로
  넘어가기 전 반드시 사용자 확인을 받을 것**
