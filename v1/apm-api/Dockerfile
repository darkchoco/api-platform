# syntax=docker/dockerfile:1

# 빌드 스테이지 - 멀티모듈 루트(pom.xml)와 apm-api 모듈만 빌드
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace

COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY apm-api/pom.xml apm-api/pom.xml
# 소스 없이 의존성만 먼저 받아 캐시 - apm-api/src만 바뀌면 이 레이어는 재사용됨
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -pl apm-api -am dependency:go-offline

COPY apm-api/src apm-api/src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -pl apm-api -am package -DskipTests

# 실행 스테이지 - JDK 대신 JRE만 담아 이미지 크기 축소
FROM eclipse-temurin:25-jre-alpine AS runtime
RUN addgroup -S apm && adduser -S apm -G apm
WORKDIR /app
COPY --from=build /workspace/apm-api/target/apm-api-*.jar app.jar
USER apm

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
