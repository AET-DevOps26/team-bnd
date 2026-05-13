# build
FROM sapmachine:25 AS build
WORKDIR /app
COPY app/ .
RUN ./gradlew bootJar --no-daemon

# run
FROM sapmachine:25-jre-headless
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
