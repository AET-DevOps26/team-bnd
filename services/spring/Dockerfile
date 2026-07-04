# syntax=docker/dockerfile:1

ARG JDK_IMAGE=amazoncorretto:25-alpine@sha256:32d81edae73e1670244827c2f12e5bcf0d335f035b538455fe9d02eb0771d41b
ARG JRE_IMAGE=azul/zulu-openjdk-alpine:25-jre-headless@sha256:e8cb8b9d571afa0fb9c459e43e9c03c05ef1993018b1c038597e4c6536ab331d


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
