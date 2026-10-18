# syntax=docker/dockerfile:1
# Build context is the repo root (needs build-logic and the version catalog):
#   docker build -f services/auth-service/Dockerfile .

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY --chmod=755 gradlew ./
COPY gradle gradle
COPY settings.gradle.kts ./
COPY build-logic build-logic
COPY services/auth-service/build.gradle.kts services/auth-service/
# Resolve dependencies in their own layer so source changes don't re-download them.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon -q :services:auth-service:dependencies > /dev/null
COPY services/auth-service/src services/auth-service/src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :services:auth-service:bootJar \
 && java -Djarmode=tools -jar services/auth-service/build/libs/auth-service-*.jar \
        extract --layers --launcher --destination /extracted

FROM eclipse-temurin:21-jre-alpine
# Pick up OS security fixes published after the base image was built.
RUN apk upgrade --no-cache
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
WORKDIR /app
# Least-frequently-changing layers first.
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
USER 10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8081
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
    CMD wget -qO- http://localhost:8081/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
