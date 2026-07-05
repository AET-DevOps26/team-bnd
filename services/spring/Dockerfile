# syntax=docker/dockerfile:1

# Build stage
FROM azul/zulu-openjdk-alpine:25@sha256:b3c19142e9989390bd6b5d0a738ab4184359752019404388e8fafe16666cd4f3 AS build

WORKDIR /app

COPY app/gradle/ gradle/
COPY app/gradlew app/build.gradle app/settings.gradle ./

RUN ./gradlew dependencies --no-daemon

COPY app/src/ src/
RUN ./gradlew bootJar --no-daemon

# Run stage
FROM azul/zulu-openjdk-alpine:25-jre-headless@sha256:e8cb8b9d571afa0fb9c459e43e9c03c05ef1993018b1c038597e4c6536ab331d

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
