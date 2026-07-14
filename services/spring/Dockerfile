# syntax=docker/dockerfile:1

# Single Dockerfile that is shared by all three Alexandria Spring microservices
# (user-service, knowledgebase-service, qa-service). The concrete service
# is specified at build time via `--build-arg SERVICE=<name>`.

# Build stage
FROM azul/zulu-openjdk-alpine:25@sha256:b3c19142e9989390bd6b5d0a738ab4184359752019404388e8fafe16666cd4f3 AS build

ARG SERVICE

WORKDIR /workspace

COPY gradle/ gradle/
COPY gradlew settings.gradle build.gradle ./
COPY user-service/build.gradle user-service/build.gradle
COPY knowledgebase-service/build.gradle knowledgebase-service/build.gradle
COPY qa-service/build.gradle qa-service/build.gradle
COPY common/build.gradle common/build.gradle
# hadolint ignore=DL3022
COPY --from=api openapi.yaml ${SERVICE}/build/openapi-input/openapi.yaml

RUN ./gradlew :${SERVICE}:dependencies --no-daemon

COPY common/src common/src
COPY ${SERVICE}/src ${SERVICE}/src
RUN ./gradlew :${SERVICE}:bootJar --no-daemon

# Run stage
FROM azul/zulu-openjdk-alpine:25-jre-headless@sha256:e8cb8b9d571afa0fb9c459e43e9c03c05ef1993018b1c038597e4c6536ab331d

ARG SERVICE

WORKDIR /app

RUN addgroup -g 1000 appgroup && adduser -u 1000 -G appgroup -D appuser

COPY --from=build /workspace/${SERVICE}/build/libs/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

# JVM settings tuned for a small resident footprint
ENV JAVA_TOOL_OPTIONS="-XX:InitialRAMPercentage=5 -XX:MaxRAMPercentage=75 -XX:MaxHeapFreeRatio=25 -Xss512k -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

# Healthcheck against actuator
HEALTHCHECK --interval=10s --timeout=5s --start-period=80s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
