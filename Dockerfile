# ==================== 1. 빌드 스테이지 ====================
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

# Gradle wrapper와 의존성 파일 먼저 복사 (캐시 활용)
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# 소스 코드 복사 후 빌드
COPY src src
RUN ./gradlew clean bootJar -x test --no-daemon

# ==================== 2. 실행 스테이지 ====================
FROM eclipse-temurin:17-jre-alpine

# 보안을 위해 non-root 유저 생성
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

WORKDIR /app

# 빌드된 JAR 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 포트 노출 (Spring Boot 기본 8080)
EXPOSE 8080

# 실행 명령어
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-Duser.timezone=Asia/Seoul", "-jar", "/app/app.jar"]
