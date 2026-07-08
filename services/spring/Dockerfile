# syntax=docker/dockerfile:1

# Single Dockerfile that is shared by all three Alexandria Spring microservices
# (user-service, knowledgebase-service, qa-service). The concrete service
# is specified at build time via `--build-arg SERVICE=<name>`.

# Build stage
FROM azul/zulu-openjdk-alpine:25@sha256:b1fe7cc8cb9735c7f03748813c66024c9e96857d9be661cfd84124719dacc100 AS build

ARG SERVICE

WORKDIR /workspace

COPY gradle/ gradle/
COPY gradlew settings.gradle build.gradle ./
COPY user-service/build.gradle user-service/build.gradle
COPY knowledgebase-service/build.gradle knowledgebase-service/build.gradle
COPY qa-service/build.gradle qa-service/build.gradle

RUN ./gradlew :${SERVICE}:dependencies --no-daemon

COPY ${SERVICE}/src ${SERVICE}/src
RUN ./gradlew :${SERVICE}:bootJar --no-daemon

# Run stage
FROM azul/zulu-openjdk-alpine:25-jre-headless@sha256:adbc43f19a14c3b19037cbd7329b4859f3c97b2b525b2cbcfbf0df9fdb64f979

ARG SERVICE

WORKDIR /app

RUN addgroup -g 1000 appgroup && adduser -u 1000 -G appgroup -D appuser

COPY --from=build /workspace/${SERVICE}/build/libs/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

# JVM settings
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

# Healthcheck against actuator
HEALTHCHECK --interval=10s --timeout=5s --start-period=80s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
