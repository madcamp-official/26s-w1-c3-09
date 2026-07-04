## ---- 빌드 단계: 프로젝트의 gradlew로 실행 JAR 생성 ----
#FROM eclipse-temurin:17-jdk AS build
#WORKDIR /app
#
## 래퍼와 빌드 설정 먼저 복사 (의존성 캐시 활용)
#COPY gradlew .
#COPY gradle gradle
#COPY build.gradle settings.gradle ./
#RUN chmod +x gradlew
#
## 소스 복사 후 실행 가능한 JAR만 생성 (bootJar → plain jar 안 만들어짐, 테스트 생략)
#COPY src src
#RUN ./gradlew clean bootJar --no-daemon
#
## ---- 실행 단계: 가벼운 JRE에 JAR만 복사 ----
#FROM eclipse-temurin:17-jre
#WORKDIR /app
#COPY --from=build /app/build/libs/*.jar app.jar
#EXPOSE 8080
#ENTRYPOINT ["java", "-jar", "app.jar"]
# 로컬(맥)에서 미리 만든 실행 JAR를 담기만 함
# → Docker 안에서 Gradle을 받지 않으므로 다운로드 timeout 회피
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]