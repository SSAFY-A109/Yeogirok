# 메인 API 이미지. backend 폴더를 빌드 컨텍스트로 쓴다.
#
#   docker build -t yeogirok-backend backend      # 저장소 루트에서
#
# 서버에서는 infra/docker-compose.yml 의 backend 서비스가 이 파일로 이미지를 만든다.
# DB 접속 정보와 프로필(SPRING_PROFILES_ACTIVE)은 실행할 때 환경 변수로 넘긴다. 이미지에는 비밀값을 넣지 않는다.

# ── 1단계: 실행 jar 빌드 ────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Gradle 설정만 먼저 복사해 의존성을 받아 둔다. src 만 바뀐 배포에서는 이 층을 캐시로 재사용한다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
# Windows 에서 체크아웃해 줄바꿈이 CRLF 가 된 gradlew 도 실행되게 한다.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY src src
# 테스트는 배포 전에 GitHub Actions 에서 실행한다. bootJar 는 -plain.jar 를 만들지 않아 jar 가 하나만 남는다.
RUN ./gradlew --no-daemon bootJar && mv build/libs/*.jar app.jar

# ── 2단계: 실행 ───────────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app

# root 가 아닌 사용자로 실행한다.
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build /workspace/app.jar app.jar
USER app

EXPOSE 8080
# 메모리 등 JVM 옵션이 필요하면 compose 에서 JAVA_TOOL_OPTIONS 환경 변수로 넘긴다(JVM 이 자동으로 읽는다).
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
