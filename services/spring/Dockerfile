# syntax=docker/dockerfile:1

# Base image digests
ARG JDK_IMAGE=eclipse-temurin:21-jdk-alpine@sha256:4fb80de7aeb277ad949cfbe89b4f504e50bb34c57fd908c5825236473d71e986
ARG JRE_IMAGE=eclipse-temurin:21-jre-alpine@sha256:704db3c40204a44f471191446ddd9cda5d60dab40f0e15c6507b815ed897238b


# Build stage
FROM ${JDK_IMAGE} AS build

WORKDIR /app

COPY app/gradle/ gradle/
COPY app/gradlew app/build.gradle app/settings.gradle ./

RUN ./gradlew dependencies --no-daemon

COPY app/src/ src/
RUN ./gradlew bootJar --no-daemon

# Run stage
FROM ${JRE_IMAGE}

WORKDIR /app

RUN addgroup -g 1000 appgroup && adduser -u 1000 -G appgroup -D appuser

COPY --from=build /app/build/libs/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

# JVM settings
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

# Healthcheck against actuator
HEALTHCHECK --interval=10s --timeout=5s --start-period=80s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
