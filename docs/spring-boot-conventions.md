# Spring Boot 프로젝트 컨벤션 가이드

> 원본 출처: *Master Spring, Spring Boot, REST, JPA, Spring Security* (eazybytes) 강의 자료 342p 중,
> 로그 규칙 · 환경 구성 · 패키지/네이밍 규칙 등 **컨벤션 관련 내용만 발췌**한 문서입니다.
> Claude Code가 Spring 프로젝트 작업 시 참조하는 용도로 작성되었으며, 필요에 따라 팀/개인 취향에 맞게 자유롭게 수정해서 사용하세요.

---

## 목차

1. [패키지 구조 & 네이밍 컨벤션](#1-패키지-구조--네이밍-컨벤션)
   - 1.1 [Convention over Configuration 원칙](#11-convention-over-configuration-원칙)
   - 1.2 [패키지 구조 접근법 2가지](#12-패키지-구조-접근법-2가지)
   - 1.3 [지원 패키지(Supporting Packages) 구성](#13-지원-패키지supporting-packages-구성)
   - 1.4 [네이밍 컨벤션](#14-네이밍-컨벤션)
   - 1.5 [실전 예시: JobPortal 패키지 구조 전체](#15-실전-예시-jobportal-패키지-구조-전체)
   - 1.6 [API 엔드포인트 그룹핑 규칙](#16-api-엔드포인트-그룹핑-규칙)
2. [로깅 규칙 (Logging Conventions)](#2-로깅-규칙-logging-conventions)
   - 2.1 [로깅 기본 원칙](#21-로깅-기본-원칙)
   - 2.2 [SLF4J & Logback](#22-slf4j--logback)
   - 2.3 [기본 로그 포맷](#23-기본-로그-포맷)
   - 2.4 [콘솔 로깅 패턴 설정 (logging.pattern.console)](#24-콘솔-로깅-패턴-설정-loggingpatternconsole)
   - 2.5 [로그 레벨 체계](#25-로그-레벨-체계)
   - 2.6 [로그 레벨 변경 & 로그 그룹](#26-로그-레벨-변경--로그-그룹)
   - 2.7 [파일 로깅 / JSON 구조화 로깅 / logback.xml](#27-파일-로깅--json-구조화-로깅--logbackxml)
   - 2.8 [로깅 코드 컨벤션](#28-로깅-코드-컨벤션)
   - 2.9 [Actuator를 통한 런타임 로그 레벨 관리](#29-actuator를-통한-런타임-로그-레벨-관리)
3. [환경 구성 컨벤션 (Environment Configuration Conventions)](#3-환경-구성-컨벤션-environment-configuration-conventions)
   - 3.1 [프로퍼티를 읽는 4가지 방식](#31-프로퍼티를-읽는-4가지-방식)
   - 3.2 [외부 설정 우선순위 (Externalized Configuration Order)](#32-외부-설정-우선순위-externalized-configuration-order)
   - 3.3 [환경변수 매핑 규칙 (Relaxed Binding)](#33-환경변수-매핑-규칙-relaxed-binding)
   - 3.4 [JVM 시스템 프로퍼티 / 커맨드라인 인자](#34-jvm-시스템-프로퍼티--커맨드라인-인자)
   - 3.5 [환경별 구성 전략 (Dev / QA / Prod)](#35-환경별-구성-전략-dev--qa--prod)
   - 3.6 [Spring Profiles](#36-spring-profiles)
   - 3.7 [조건부 빈 생성 (Conditional Bean Creation)](#37-조건부-빈-생성-conditional-bean-creation)
   - 3.8 [Actuator 환경/설정 관련 엔드포인트](#38-actuator-환경설정-관련-엔드포인트)
4. [기타 컨벤션](#4-기타-컨벤션)
   - 4.1 [Validation Best Practices](#41-validation-best-practices)

---

## 1. 패키지 구조 & 네이밍 컨벤션

### 1.1 Convention over Configuration 원칙

Spring Boot는 "Convention over Configuration(설정보다 관습)" 원칙을 따른다. 개발자가 모든 것을 수동으로 설정하지 않아도 되도록 합리적인 기본값(sensible defaults)을 제공한다.

- 무겁고 긴 XML 설정 대신, classpath에 존재하는 의존성과 일반적인 유스케이스 기본값을 기반으로 자동 설정(auto-configure)한다.
- 개발자는 보일러플레이트가 아닌 비즈니스 로직에 집중할 수 있다.

**Auto-Configuration 시 기본으로 가정되는 값 예시:**
```properties
server.port=8080
server.servlet.context-path=/
logging.level.root=INFO
```

### 1.2 패키지 구조 접근법 2가지

Spring Boot는 특정 패키지 구조를 강제하지 않지만, 클린 아키텍처 관행을 따르면 프로젝트가 확장 가능하고 가독성이 좋아진다.

**기본 규칙:** 메인 애플리케이션 클래스는 루트 패키지에 위치시켜, Spring Boot가 모든 컴포넌트를 자동으로 스캔할 수 있도록 한다.

```
com.example.myapp
└── MyAppApplication.java
```

두 가지 대표적인 접근법:

**① By Layer (전통적 계층형 아키텍처)** — 기술적 역할(role)로 코드를 조직한다.

```
com.example.myapp
 ├── controller
 ├── service
 ├── repository
 ├── entity
 ├── exception
 └── config
```

- 장점: 이해하기 쉬움 / 중소규모 프로젝트에 적합 / 흔하고 초보자 친화적
- 단점: 대규모 도메인 확장에 불리함 / 비즈니스 로직이 여러 레이어에 흩어짐

**② By Feature / Domain (기능/도메인별, 마이크로서비스 및 대규모 앱에 권장)** — 비즈니스 기능 단위로 파일을 그룹핑한다.

```
com.example.myapp
 ├── user
 │    ├── controller
 │    ├── service
 │    ├── repository
 │    ├── entity
 │    └── dto
 └── job
      ├── controller
      ├── service
      ├── repository
      ├── entity
      └── dto
```

- 장점: 높은 모듈성 / 유지보수와 확장 용이 / 팀 협업 용이 / 도메인 주도 설계(DDD) 개선
- 단점: 초기 설계에 조금 더 많은 계획이 필요함

### 1.3 지원 패키지(Supporting Packages) 구성

도메인 패키지 외에 아래와 같은 공통/지원 목적의 패키지를 별도로 둔다.

```
com.example.myapp
 ├── common     # 재사용 가능한 헬퍼
 ├── config     # 각종 설정(Configuration)
 ├── security   # 시큐리티 설정, 필터
 ├── exception  # 에러 처리
 └── util       # 유틸리티
```

### 1.4 네이밍 컨벤션

- 패키지명은 소문자로 작성한다.
- 도메인명은 복수형을 피한다. (예: `jobs`가 아니라 `job`)
- 네이밍은 일관성 있게 유지한다.

### 1.5 실전 예시: JobPortal 패키지 구조 전체

```
com.example.jobportal
├── JobPortalApplication.java
│
├── common
│   ├── dto
│   ├── exception
│   ├── util
│   └── mapper
│
├── config
│   ├── security
│   └── web
│
├── auth
│   ├── controller
│   ├── service
│   ├── dto
│   ├── entity
│   ├── repository
│   ├── mapper
│   ├── jwt
│   └── model
│
├── user
│   ├── controller
│   ├── service
│   ├── dto
│   ├── entity
│   ├── repository
│   ├── mapper
│   └── enums
```

**인증/인가(Authentication & Authorization) 패키지 상세**
```
auth
├── AuthController
├── AuthService
├── entity (UserAuth / LoginHistory)
├── jwt
│   ├── JwtFilter
│   └── JwtUtils
├── dto
├── repository
└── mapper
```

**config 패키지 상세**
```
config
├── security
│   ├── SecurityConfig
│   ├── CustomUserDetailsService
│   ├── AuthenticationEntryPoint
│   ├── RoleHierarchyConfig
│   └── CorsConfig
└── web
    ├── OpenApiConfig
    └── WebConfig
```

### 1.6 API 엔드포인트 그룹핑 규칙

무작위 엔드포인트 대신, 도메인 단위로 명확하게 그룹핑한다.

```
/auth/...
/jobs/...
/companies/...
/users/...
/employers/...
/admin/...
/support/...
```

---

## 2. 로깅 규칙 (Logging Conventions)

### 2.1 로깅 기본 원칙

로깅은 애플리케이션에서 중요한 이벤트를 기록하는 과정이다. 디버깅, 모니터링, 트러블슈팅에 도움을 준다. 로그는 오류, 경고, 실행 흐름, 성능 관련 정보를 제공한다.

**로깅의 핵심 이점**
1. Debugging — 오류를 찾고 수정하는 데 도움
2. Monitoring — 애플리케이션 동작을 시간에 따라 추적
3. Auditing — 중요한 작업에 대한 기록 보관
4. Performance Analysis — 앱의 느린 부분을 식별하는 데 도움

**`System.out.println()`을 사용하면 안 되는 이유**

`System.out`은 다음을 제공하지 않는다:
- 로그 레벨 없음
- 파일 로깅 없음
- 포매팅 없음
- 프로덕션에 적합하지 않음

→ 실제 애플리케이션에서 `System.out.println()`은 허용되지 않는다.

### 2.2 SLF4J & Logback

Spring Boot는 기본적으로 **SLF4J(Simple Logging Facade for Java)** 와 **Logback**을 사용한다. 별도 의존성 추가 없이 바로 동작한다.

**SLF4J란?**
SLF4J는 Spring Boot가 로그를 기록할 때 사용하는 공통 로깅 인터페이스로, 내부적으로 어떤 로깅 프레임워크를 쓰는지 신경 쓰지 않아도 된다.

| 프레임워크 | 설명 |
|---|---|
| SLF4J (Facade) | 범용 로깅 API (베스트 프랙티스로 권장) |
| Logback (Default) | Spring Boot의 기본 로거 |
| Log4j2 | 강력하고 기능이 풍부한 로깅 |
| Java Util Logging (JUL) | 자바 내장 로깅 |

코드가 Logback에 직접 의존하면:
- 이후 Log4j2로 전환하기 어려워짐
- 코드가 특정 구현체와 강하게 결합됨

**SLF4J가 해결하는 문제**
- SLF4J = Simple Logging Facade for Java
- Facade는 "wrapper / front door"를 의미
- 하나의 공통 로깅 API를 제공
- 내부적으로 실제 로깅 프레임워크로 로그를 전달(forward)

### 2.3 기본 로그 포맷

```
2100-11-20T16:37:12.913Z INFO 127185 --- [myapp] [main] com.example.MyApp : Application started successfully!
```

로그에 출력되는 항목:
- **Date and Time**: 밀리초 단위, 정렬(sort) 가능
- **Log Level**: `ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE`
- **Process ID**
- **`---` 구분자**: 실제 로그 메시지 시작을 구분
- **Application name**: 대괄호로 표시 (`spring.application.name` 설정 시에만 기본 출력)
- **Application group**: 대괄호로 표시 (`spring.application.group` 설정 시에만 기본 출력)
- **Thread name**: 대괄호로 표시 (콘솔 출력 시 축약될 수 있음)
- **Correlation ID**: 트레이싱 활성화 시 표시
- **Logger name**: 보통 소스 클래스명 (축약된 형태)
- **로그 메시지**

### 2.4 콘솔 로깅 패턴 설정 (`logging.pattern.console`)

`logging.pattern.console`은 콘솔에 로그가 표시되는 방식을 커스터마이징하는 Spring Boot 프로퍼티다.

- 콘솔 출력에서 로그 메시지가 어떻게 보일지 제어
- 로그 항목의 포맷, 구조, 스타일을 정의
- Spring Boot 내장 로깅 설정의 일부

**예시**
```properties
logging.pattern.console=${CONSOLE_LOG_PATTERN:
  %green(%d{HH:mm:ss.SSS}) %blue(%-5level)
  %red([%thread]) %yellow(%logger{15}) - %cyan(%msg%n)}
```

이 값은 환경변수 치환(environment variable substitution) 문법을 사용한다:
- `CONSOLE_LOG_PATTERN` → 환경변수 이름
- 값이 제공되지 않으면 → 기본 패턴 사용

**패턴 구성 요소 설명**

| Component | Purpose | Example output |
|---|---|---|
| `%green(%d{HH:mm:ss.SSS})` | 초록색 타임스탬프 | `14:23:45.678` |
| `%blue(%-5level)` | 파란색 로그 레벨 (5자, 좌측 정렬) | `INFO`, `ERROR` |
| `%red([%thread])` | 빨간색 스레드명 | `[main]` |
| `%yellow(%logger{15})` | 노란색 로거명 (최대 15자) | `c.e.UserService` |
| `%cyan(%msg)` | 청록색 실제 로그 메시지 | `User created successfully` |
| `%n` | 줄바꿈 문자 | — |

**환경변수 문법 구조**
- 구조: `${ENV_VARIABLE:default_value}`
- 예시: `${CONSOLE_LOG_PATTERN:fallback}`
- 콜론(`:`) 앞: 환경변수 이름 / 콜론 뒤: 환경변수가 설정되지 않았을 때의 fallback/기본값

**이점**
- 코드 변경 없이 오버라이드 가능
- 환경별로 다른 패턴 적용 가능
  - Development: 상세한 컬러 로그
  - Production: 로그 수집기(log aggregator)를 위한 JSON 포맷
  - Testing: 최소한의 로깅

또 다른 패턴 예시 (`%thread`, `%logger{36}` 사용):
```properties
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36}
```
예시 출력:
```
2100-11-20 10:30:45 [main] INFO com.example.MyService - Application started!
```

### 2.5 로그 레벨 체계

Spring Boot는 기본적으로 root 로그 레벨을 `INFO`로 설정한다.

| Log Level | Description | Example Use Case |
|---|---|---|
| TRACE | 가장 상세한 로그 | 메서드 호출 추적 |
| DEBUG | 디버깅 정보 | 변수 값, 실행 단계 |
| INFO | 일반적인 애플리케이션 정보 | 시작 메시지, 비즈니스 이벤트 |
| WARN | 잠재적인 문제 | Deprecated API 사용, 높은 메모리 사용량 |
| ERROR | 심각한 문제 | 예외, 실패한 트랜잭션 |

**설정된 레벨에 따라 표시되는 로그**

| Configured Log Level | Logs That Appear |
|---|---|
| TRACE | TRACE, DEBUG, INFO, WARN, ERROR 모두 표시 |
| DEBUG | DEBUG, INFO, WARN, ERROR 표시 |
| INFO | INFO, WARN, ERROR 표시 |
| WARN | WARN, ERROR 표시 |
| ERROR | ERROR만 표시 |

### 2.6 로그 레벨 변경 & 로그 그룹

**`application.properties`에서 로그 레벨 변경**

애플리케이션 전체 로그 레벨 설정:
```properties
logging.level.root=DEBUG
```

특정 패키지의 로그 레벨 설정:
```properties
logging.level.com.example.myapp=DEBUG
```

**로그 그룹(Log Group)** — 여러 패키지에 하나의 로그 레벨을 한 번에 지정할 수 있다.

```properties
# 논리적인 이름으로 로그 그룹 생성
logging.group.jobportal_error=com.eazybytes.jobportal.aspects,\
com.eazybytes.jobportal.controller

# 해당 그룹의 로그 레벨 설정
logging.level.jobportal_error=ERROR
```

### 2.7 파일 로깅 / JSON 구조화 로깅 / logback.xml

기본적으로 로그는 콘솔에 출력된다. 파일로 저장하려면:

```properties
# 로그를 파일로 저장
logging.file.name=/Users/eazybytes/Desktop/logs/app.log
```

**구조화된 로깅을 위한 JSON 로그 사용** — ELK Stack이나 Cloud Logging에서 쉽게 파싱하기 위해 JSON 로그를 활성화할 수 있다.

```properties
logging.pattern.file={"timestamp":"%d{yyyy-MM-dd HH:mm:ss}",
                       "level":"%p","logger":"%c","message":"%m"}
```

**`logback.xml`** — 고급 사용 사례를 위한 설정 파일로, 로그가 어떻게 기록되는지 제어한다.

`logback.xml`을 사용하면 다음을 할 수 있다:
- 로그가 어디로 가는지 결정 (콘솔 / 파일)
- 로그가 어떻게 보이는지 결정 (포맷)
- 얼마나 로그를 남길지 결정 (INFO / ERROR / DEBUG)
- 로그 로테이션(log rotation) 제어

이 옵션을 사용하려면 `logback.xml`을 `src/main/resources/` 아래에 위치시키면 된다.

### 2.8 로깅 코드 컨벤션

**기본 SLF4J 방식**
```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/logging")
public class LoggingController {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingController.class);

    @GetMapping
    public ResponseEntity<String> testLogging() {
        LOGGER.trace(" TRACE: This is a very detailed trace log. Used for tracking execution flow.");
        LOGGER.debug(" DEBUG: This is a debug message. Used for debugging.");
        LOGGER.info(" INFO: This is an informational message. Application events.");
        LOGGER.warn(" WARN: This is a warning! Something might go wrong.");
        LOGGER.error(" ERROR: An error occurred! This needs immediate attention.");
        return ResponseEntity.status(HttpStatus.OK)
                .body("Logging tested successfully");
    }
}
```

**Lombok `@Slf4j` 사용 방식 (권장)**
```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/logging")
@Slf4j
public class LoggingController {

    @GetMapping
    public ResponseEntity<String> testLogging() {
        log.trace(" TRACE: This is a very detailed trace log. Used for tracking execution flow.");
        log.debug(" DEBUG: This is a debug message. Used for debugging.");
        log.info(" INFO: This is an informational message. Application events.");
        log.warn(" WARN: This is a warning! Something might go wrong.");
        log.error(" ERROR: An error occurred! This needs immediate attention.");
        return ResponseEntity.status(HttpStatus.OK)
                .body("Logging tested successfully");
    }
}
```

> Lombok `@Slf4j`는 `log`라는 이름의 로거 변수를 자동으로 생성해준다. `LoggerFactory`를 직접 쓸 필요가 없다.

### 2.9 Actuator를 통한 런타임 로그 레벨 관리

`/actuator/loggers` 엔드포인트를 사용하면 애플리케이션을 재시작하지 않고도 런타임에 로그 레벨을 조회/변경할 수 있다.

**로그 레벨 설정하기**

특정 로거의 레벨을 설정하려면, `configuredLevel`을 지정한 JSON body로 `/actuator/loggers/{logger.name}`에 POST 요청을 보낸다.

```bash
curl 'http://localhost:8080/actuator/loggers/com.example' -i -X POST \
  -H 'Content-Type: application/json' \
  -d '{"configuredLevel":"debug"}'
```
→ `com.example` 로거의 `configuredLevel`을 `DEBUG`로 설정한다.

**로그 레벨 초기화하기**

로거의 레벨을 초기화하려면, 빈 객체(`{}`)를 담은 JSON body로 동일한 엔드포인트에 POST 요청을 보낸다.

```bash
curl 'http://localhost:8080/actuator/loggers/com.example' -i -X POST \
  -H 'Content-Type: application/json' -d '{}'
```
→ `com.example` 로거의 설정된 레벨을 초기화(clear)한다.

---

## 3. 환경 구성 컨벤션 (Environment Configuration Conventions)

### 3.1 프로퍼티를 읽는 4가지 방식

Spring Boot에서 프로퍼티를 읽는 데 가장 흔히 쓰이는 접근법은 아래 4가지다.

**① `@Value` 어노테이션** — 개별 프로퍼티 값을 빈(bean)에 직접 주입한다. 특정 필드에 개별 프로퍼티를 주입할 때 적합하다.

```java
@Value("${property.name}")
private String propertyValue;
```

**② `Environment` 인터페이스** — 애플리케이션 환경으로부터 프로퍼티에 접근하는 메서드를 제공한다. `Environment` 빈을 주입받아 메서드로 프로퍼티 값을 조회한다. 더 유연하며 프로그래밍적으로 프로퍼티 키에 접근할 수 있다.

```java
@Autowired
private Environment env;

public void getProperty() {
    String propertyValue = env.getProperty("property.name");
}
```

**③ `@ConfigurationProperties` (권장 방식)** — 프로퍼티 하드코딩을 피할 수 있어 권장되는 접근법이다. 프로퍼티 그룹 전체를 하나의 빈에 바인딩할 수 있다. 프로퍼티와 일치하는 필드를 가진 설정 클래스를 정의하면, Spring Boot가 자동으로 프로퍼티를 해당 필드에 매핑한다.

```java
@ConfigurationProperties("prefix")
public class MyConfig {
    private String property;
    // getters and setters
}
```

> 위 기능을 활성화하려면 Spring Boot 메인 클래스 위에 `@EnableConfigurationProperties`를 명시해야 한다.

**`@Value` 실전 예시**
```java
@Configuration
public class CaffeineCacheConfig {

    @Value("${cache.jobs.ttl-minutes:5}")
    private int jobsCacheTtlMinutes;

    @Value("${cache.jobs.max-size:2000}")
    private int jobsCacheMaxSize;

    @Bean
    public CacheManager caffeineCacheManager() {
        CaffeineCache jobsCache = new
                CaffeineCache("jobs", Caffeine.newBuilder()
            .expireAfterWrite(jobsCacheTtlMinutes, TimeUnit.MINUTES)
                        .maximumSize(jobsCacheMaxSize).build());

        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(Arrays.asList(jobsCache));
        return manager;
    }
}
```
```properties
# application.properties
cache.jobs.ttl-minutes=10
cache.jobs.max-size=5000
```

**`@ConfigurationProperties` 실전 예시**

POJO 클래스:
```java
@ConfigurationProperties("app.cors")
@Getter @Setter
public class CorsProperties {
    private List<String> allowedOrigins;
    private List<String> allowedMethods;
    private List<String> allowedHeaders;
    private Boolean allowCredentials;
    private Long maxAge;
}
```
```properties
# application.properties (공통 prefix 사용)
app.cors.allowed-origins=http://localhost:5173
app.cors.allowed-methods=*
app.cors.allowed-headers=*
app.cors.allow-credentials=true
app.cors.max-age=3600
```

Spring Boot 메인 클래스에서 활성화:
```java
@SpringBootApplication
@EnableConfigurationProperties(value = {CorsProperties.class})
public class JobportalApplication {
    public static void main(String[] args) {
        SpringApplication.run(JobportalApplication.class, args);
    }
}
```

사용 예시 (주입 후 사용):
```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class JobPortalSecurityConfig {

    private final CorsProperties corsProperties;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.getAllowedOrigins());
        config.setAllowedMethods(corsProperties.getAllowedMethods());
        config.setAllowedHeaders(corsProperties.getAllowedHeaders());
        config.setAllowCredentials(corsProperties.getAllowCredentials());
        config.setMaxAge(corsProperties.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

**④ `@PropertySource`** — 외부 파일로부터 프로퍼티를 로드하도록 지시하는 어노테이션. 설정을 코드와 분리해서 관리할 수 있게 해준다.

**사용 목적**
- `application.properties` 외의 커스텀 프로퍼티 파일을 로드하기 위해
- 설정을 논리적으로 구성하기 위해

**사용법** — Spring이 관리하는 빈 클래스에 `@PropertySource`를 추가한다.
```java
@Component
@PropertySource("classpath:jwt.properties")
public class JwtUtil {
}
```
→ `src/main/resources`에서 `jwt.properties` 파일을 로드한다.

**`@Value`와 함께 프로퍼티 접근**
```java
@Component
@PropertySource("classpath:jwt.properties")
public class JwtUtil {

    @Value("${jwt.issuer:Job Portal}")
    private String jwtIssuer;

    @Value("${jwt.subject:JWT Token}")
    private String jwtSubject;

    @Value("${jwt.expiration.hours:1}")
    private int jwtExpirationHours;
}
```

여러 파일을 동시에 로드할 수도 있다:
```java
@PropertySource({"classpath:db.properties",
                  "classpath:app.properties"})
```

**`ignoreResourceNotFound` 옵션**

기본적으로 지정한 프로퍼티 파일이 존재하지 않으면 Spring은 에러를 던진다. `ignoreResourceNotFound = true`를 사용하면 이 에러를 피할 수 있으며, 파일이 선택적(optional)인 경우 애플리케이션을 더 유연하게 만든다.

```java
@Configuration
@PropertySource(
    value = "classpath:optional-config.properties",
    ignoreResourceNotFound = true
)
public class AppConfig {
}
```

**언제 사용하는가?**
- 파일이 선택적(optional)일 때
- 모든 환경에 존재하지 않을 수 있는 외부 프로퍼티 파일일 때
- 애플리케이션 시작 실패(startup failure)를 방지하고 싶을 때

### 3.2 외부 설정 우선순위 (Externalized Configuration Order)

Spring Boot는 설정/프로퍼티를 외부화(externalize)할 수 있게 해주므로, 동일한 코드가 여러 환경에서 동작할 수 있다. 기본적으로 Spring Boot는 classpath에서 `application.properties` 또는 `application.yml`을 찾는다.

Spring Boot는 설정에 대해 특정한 우선순위(order of precedence)를 따른다. **나중 항목이 이전 항목을 오버라이드한다.**

1. `application.properties` 또는 `application.yml` 내 프로퍼티
2. 운영체제 환경변수 (OS environment variables)
3. Java 시스템 프로퍼티 (`System.getProperties()`)
4. JNDI 속성 (`java:comp/env`)
5. ServletContext 초기화 파라미터
6. ServletConfig 초기화 파라미터
7. 커맨드라인 인자 (Command-line arguments)

이 순서 덕분에 코드를 변경하지 않고도 환경별로 값을 쉽게 오버라이드할 수 있다.

### 3.3 환경변수 매핑 규칙 (Relaxed Binding)

환경변수는 모든 운영체제에서 동작하기 때문에 외부 설정 방식으로 널리 사용된다. Java는 `System.getenv()`로 이를 읽을 수 있다.

**환경변수를 Spring 프로퍼티로 매핑하는 규칙**
- 모든 글자를 대문자로 변경
- 점(`.`)과 대시(`-`)를 언더스코어(`_`)로 치환

**예시**
```
SPRING_JPA_SHOW_SQL  →  spring.jpa.show-sql
```

이러한 자동 변환을 Spring Boot에서는 **relaxed binding**이라고 부른다.

Windows:
```
set SPRING_JPA_SHOW_SQL=false
java -jar jobportal.jar
```

Linux 계열 OS:
```
SPRING_JPA_SHOW_SQL=false java -jar jobportal.jar
```

### 3.4 JVM 시스템 프로퍼티 / 커맨드라인 인자

**JVM 시스템 프로퍼티**

JVM 시스템 프로퍼티도 Spring 프로퍼티를 오버라이드할 수 있지만, 커맨드라인 인자보다는 우선순위가 약간 낮다. 앱 시작 시 `-Dproperty.name=value` 형태로 JVM 프로퍼티를 전달할 수 있으며, JAR을 다시 빌드하지 않고도 설정을 변경할 수 있다.

```bash
java -Dspring.jpa.show-sql=false -jar jobportal.jar
```

> JVM 프로퍼티와 커맨드라인 인자가 동시에 제공되면, Spring Boot는 우선순위가 가장 높은 커맨드라인 인자 값을 사용한다.

**커맨드라인 인자**

Spring Boot는 커맨드라인 인자를 키/값 쌍으로 변환하여 `Environment` 객체에 추가한다. 프로덕션 환경에서 이 인자들은 가장 높은 우선순위를 가진다.

```bash
java -jar jobportal.jar --spring.jpa.show-sql=false
```

> 커맨드라인 인자는 대응되는 Spring 프로퍼티와 동일한 네이밍 규칙을 따르며, CLI 인자에 익숙한 `--` 접두사를 사용한다.

### 3.5 환경별 구성 전략 (Dev / QA / Prod)

예를 들어 백엔드 앱이 여러 환경에 걸쳐 아래와 같은 요구사항을 가질 수 있다:

- **Development**: jobs 캐시 TTL 10분, SQL 로깅 활성화, JWT 토큰 만료 24시간
- **Testing**: jobs 캐시 TTL 15분, SQL 로깅 활성화, JWT 토큰 만료 24시간
- **Production**: jobs 캐시 TTL 5분, SQL 로깅 비활성화, JWT 토큰 만료 1시간

배포 전에 `application.properties` 파일을 수동으로 변경하는 것은 번거롭고 오류가 발생하기 쉬운 프로세스다. 더 나은 자동화된 접근법이 필요하다.

> 하나의 앱 코드베이스(공통으로 빌드/패키징됨)에 대해, Development Config / QA Config / Prod Config 를 각 환경(Development Env / QA Env / Prod Env)에 맞게 분리 적용하는 구조.

### 3.6 Spring Profiles

**Spring Boot Profiles란?**

Spring Boot 프로파일을 사용하면 하나의 애플리케이션 안에서 서로 다른 환경을 위한 여러 설정을 만들 수 있다.

예시 환경:
- Development (`dev`)
- Testing (`test`)
- Staging (`staging`)
- Production (`prod`)

각 환경마다 서로 다른 프로퍼티, 빈, 동작을 정의할 수 있다.

**프로파일이 왜 필요한가?**

애플리케이션이 다음과 같이 실행된다고 상상해보자:
- 개발자 로컬 PC에서 → 로컬 데이터베이스 사용
- QA 서버에서 → 테스트 데이터베이스 사용
- 프로덕션에서 → 보안이 적용된 프로덕션 데이터베이스 사용

→ 코드는 동일하지만 설정이 다르다. 프로파일은 환경 간 수동 코드 변경을 방지해준다.

**비유 — 계절에 맞는 옷**

Spring 프로파일을 계절별 옷차림처럼 생각해보면 이해하기 쉽다.
- Summer profile → 가벼운 옷
- Winter profile → 따뜻한 옷
- Rainy profile → 우비와 장화

몸(애플리케이션)은 동일하지만, 날씨(환경)에 따라 옷차림(프로파일 설정)이 바뀐다. 마찬가지로 애플리케이션은 동일하지만, 프로파일이 실행 환경에 맞게 설정을 바꿔준다.

**프로파일로 무엇을 바꿀 수 있는가?**

거의 모든 것을 커스터마이징할 수 있다:
- `application.properties` 파일
- 데이터베이스 연결
- API URL
- 로깅 레벨
- 빈 생성 (조건부 빈)

**프로파일 정의 방법**

`application-<profile>.yml` 또는 `application-<profile>.properties` 형태의 파일에 프로파일별 프로퍼티를 정의한다.

예시:
```
application-prod.properties   # prod 환경/프로파일용
application-qa.properties     # qa 환경/프로파일용
```

기본(default) 프로파일은 항상 활성화되어 있다. Spring Boot는 `application.properties`의 모든 프로퍼티를 기본 프로파일로 로드한다.

`spring.profiles.active` 프로퍼티를 통해 특정 프로파일을 활성화할 수 있으며, 해당 파일 안에 직접 명시하거나 CLI/환경변수 등으로 외부에서 제공할 수도 있다.

**프로파일 활성화 방법**
```
Command-line:        --spring.profiles.active=prod
Environment variable: SPRING_PROFILES_ACTIVE=prod
Application properties: spring.profiles.active=prod
```

**활성 프로파일 기반 조건부 로직 실행 예시**
```java
@Value("${jwt.expiration.hours:1}")
private int jwtNonProdExpirationHours;

@Value("${jwt.prod.expiration.hours:1}")
private int jwtProdExpirationHours;

List<String> profiles = Arrays.asList(env.getActiveProfiles());

int jwtExpirationHours;
if (profiles.contains("prod")) {
    jwtExpirationHours = jwtProdExpirationHours;
} else {
    jwtExpirationHours = jwtNonProdExpirationHours;
}
```

### 3.7 조건부 빈 생성 (Conditional Bean Creation)

**조건부 빈 생성이란?**

특정 조건이 참일 때만 빈을 생성하고 싶은 경우가 있다. Spring Boot는 빈을 조건부로 생성할 수 있는 강력한 어노테이션들을 제공한다.

다음과 같은 경우에 유용하다:
- 설정 전환
- 특정 환경에서만 기능 활성화
- 프로덕션에서 불필요한 빈 생성 방지

**`@ConditionalOnProperty`** — 프로퍼티가 존재하거나 특정 값을 가질 때만 빈을 생성한다.
```java
@Bean
@ConditionalOnProperty(name = "feature.enabled",
                        havingValue = "true")
public MyService myService() {
    return new MyService();
}
```
→ `application.properties`에 `feature.enabled=true`를 설정하면 빈이 생성된다.

**`@Profile`** — 특정 프로파일에서만 빈을 생성한다.
```java
@Profile("dev")
@Bean
public DataSource devDataSource() {
    return new DevDataSource();
}
```
→ 이 빈은 `spring.profiles.active=dev`일 때만 활성화된다.

**`@ConditionalOnMissingBean`** — 해당 빈이 아직 존재하지 않을 때만 빈을 생성한다.
```java
@Bean
@ConditionalOnMissingBean
public MyService myService() {
    return new MyService();
}
```
→ 사용자가 오버라이드할 수 있는 기본 빈을 제공할 때 유용하다.

**`@ConditionalOnClass`** — classpath에 특정 클래스가 존재할 때만 빈을 생성한다.
```java
@Bean
@ConditionalOnClass(name = "com.example.SomeLibrary")
public MyLibraryBean myLibraryBean() {
    return new MyLibraryBean();
}
```
→ 오토 설정(auto-configuration)에 유용하다.

**조건부 빈의 이점**
- 깔끔하고 유연한 설정
- 필요할 때만 기능 활성화
- 메모리와 시작 시간 절약
- 중복 빈 방지

> 위 개념들은 스테레오타입 어노테이션(`@Component`, `@Service` 등)에도 동일하게 적용 가능하다.

### 3.8 Actuator 환경/설정 관련 엔드포인트

Spring Boot Actuator는 Spring Boot 애플리케이션을 모니터링하고 관리하는 데 도움을 주는 도구다. 앱의 상태(health), 메트릭, 상태를 확인할 수 있는 기성 REST API를 제공한다.

Actuator로 할 수 있는 것:
- 앱이 정상적으로 실행 중인지 확인
- 시스템 메트릭 조회 (메모리, CPU, 스레드)
- HTTP 요청, DB 연결 모니터링
- 로드된 Spring Bean 확인

**의존성 추가**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

기본적으로는 헬스체크(`/actuator/health`) 엔드포인트만 활성화되어 있다. 더 많은 엔드포인트를 노출하려면:
```properties
management.endpoints.web.exposure.include=*
```

커스텀 정보 추가:
```properties
management.info.env.enabled=true
info.app.name=jobportal
info.app.description=Job Portal REST API
info.app.version=1.0.0
```
→ `/actuator/info` 방문 시:
```json
{
  "app": {
    "name": "jobportal",
    "description": "Job Portal REST API",
    "version": "1.0.0"
  }
}
```

**주요 Actuator 엔드포인트 목록**

| Endpoint | 설명 |
|---|---|
| `/actuator/health` | 애플리케이션과 의존성의 전체 상태(health) 표시 |
| `/actuator/info` | 빌드/버전 등 커스텀 애플리케이션 정보 표시 |
| `/actuator/metrics` | 메모리, CPU, HTTP 요청 통계 등 런타임 메트릭 제공 |
| `/actuator/env` | 애플리케이션 환경 프로퍼티와 현재 값 노출 |
| `/actuator/beans` | 애플리케이션 컨텍스트에 로드된 모든 Spring 빈 목록 |
| `/actuator/mappings` | 모든 HTTP 엔드포인트와 요청 매핑 표시 |
| `/actuator/loggers` | 런타임에 로깅 레벨 조회/변경 가능 |
| `/actuator/threaddump` | 실행 중인 모든 스레드의 스냅샷과 상태 표시 |
| `/actuator/conditions` | 오토 설정 조건에 따라 빈이 생성/스킵된 이유 표시 |
| `/actuator/configprops` | `@ConfigurationProperties` 클래스에 바인딩된 모든 설정 프로퍼티 표시 |
| `/actuator/sbom` | 애플리케이션이 사용하는 모든 의존성 목록(Software Bill of Materials) 노출 |
| `/actuator/scheduledtasks` | 설정된 모든 스케줄 작업/cron task 목록 |
| `/actuator/caches` | 애플리케이션이 사용 중인 모든 캐시와 캐시 매니저 표시 |

**Actuator는 안전한가?**

기본적으로:
- 민감한 데이터는 보호된다.
- `health`와 `info`만 공개(public)로 열려 있다.
- Spring Security를 사용해 접근을 제한할 수 있다.

기본적으로 모든 웹 엔드포인트는 `/actuator` 경로 하위에 노출된다. base path는 아래 프로퍼티로 변경 가능하다:
```properties
management.endpoints.web.base-path=/manage
```
→ 위 프로퍼티 적용 시 info 엔드포인트 URL은 `/manage/info`가 된다.

**Actuator 활용 예시 (curl)**

모든 캐시 비우기:
```bash
curl 'http://localhost:8080/actuator/caches' -i -X DELETE
```

이름으로 특정 캐시 비우기:
```bash
curl 'http://localhost:8080/actuator/caches/countries' \
  -i -X DELETE -H 'Content-Type: application/x-www-form-urlencoded'
```

애플리케이션 종료:
```bash
curl 'http://localhost:8080/actuator/shutdown' -i -X POST
```

> 로그 레벨 설정/초기화 예시는 [2.9 Actuator를 통한 런타임 로그 레벨 관리](#29-actuator를-통한-런타임-로그-레벨-관리) 참고.

---

## 4. 기타 컨벤션

### 4.1 Validation Best Practices

- 요청(Request) 검증(validation) 시 엔티티 클래스 대신 **DTO**를 사용한다.
- 클라이언트에게 항상 의미 있는(meaningful) 에러 메시지를 반환한다.
- `@RestControllerAdvice`를 사용해 검증 에러 처리를 중앙 집중화(centralize)한다.

---

*이 문서는 원본 342페이지 강의 자료 중 컨벤션 관련 섹션만 발췌·정리한 것입니다. 필요에 맞게 자유롭게 수정해서 사용하세요.*
