# syntax=docker/dockerfile:1

# Base image digests
ARG JDK_IMAGE=eclipse-temurin:21-jdk-alpine@sha256:4fb80de7aeb277ad949cfbe89b4f504e50bb34c57fd908c5825236473d71e986
ARG JRE_IMAGE=eclipse-temurin:21-jre-alpine@sha256:704db3c40204a44f471191446ddd9cda5d60dab40f0e15c6507b815ed897238b

# OCI labels, filled by CI
ARG OCI_SOURCE=""
ARG OCI_REVISION=""
ARG OCI_CREATED=""
ARG OCI_LICENSES="MIT"
ARG OCI_TITLE="alexandria-spring"
ARG OCI_DESCRIPTION="Spring Boot backend for Alexandria"

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

LABEL org.opencontainers.image.source="${OCI_SOURCE}" \
      org.opencontainers.image.revision="${OCI_REVISION}" \
      org.opencontainers.image.created="${OCI_CREATED}" \
      org.opencontainers.image.licenses="${OCI_LICENSES}" \
      org.opencontainers.image.title="${OCI_TITLE}" \
      org.opencontainers.image.description="${OCI_DESCRIPTION}"

WORKDIR /app

RUN addgroup -g 1000 appgroup && adduser -u 1000 -G appgroup -D appuser

COPY --from=build /app/build/libs/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

# JVM settings
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

# Healthcheck against actuator
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
