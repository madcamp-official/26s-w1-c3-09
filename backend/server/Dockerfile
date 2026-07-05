# 빌드 컨텍스트는 backend/ (server가 아님!) — backend/config를 이미지에 담기 위함
#   docker build -f server/Dockerfile .          (backend/ 에서)
#   compose에서는 context: ./backend, dockerfile: server/Dockerfile

# ---- 빌드 단계: 프로젝트의 gradlew로 실행 JAR 생성 ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# 래퍼와 빌드 설정 먼저 복사 (소스만 바뀌면 의존성 레이어 캐시 재사용)
COPY server/gradlew .
COPY server/gradle gradle
COPY server/build.gradle server/settings.gradle ./
RUN chmod +x gradlew

# 소스 복사 후 실행 가능한 JAR만 생성 (테스트는 CI/로컬 몫 — 여기선 생략)
COPY server/src src
RUN ./gradlew clean bootJar --no-daemon

# ---- 실행 단계: 가벼운 JRE + JAR + 공용 설정 ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

# server·batch 공용 설정 (scoring.json 등) — 없으면 부팅 실패 (ConfigFileLoader)
COPY config /app/config
ENV CONFIG_DIR=/app/config

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

# 참고: 예전 "로컬에서 미리 bootJar 만들어 COPY" 방식은 로컬 Gradle 다운로드
# timeout 회피용이었음. EC2에서 빌드하면 문제 없어 멀티스테이지로 복원.
