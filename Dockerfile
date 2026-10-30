# syntax=docker/dockerfile:1
# Builds and runs the app: docker build -t limelight-auth . && docker run -p 8080:8080 --env-file .env limelight-auth
# (Vercel and similar hosts build this file themselves.)

FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY . .
# Tests run in GitHub Actions; this only builds the jar (Gradle also downloads Tailwind here).
# The cache mount keeps Gradle's downloads between builds, where the builder supports it.
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon --console=plain

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 app
COPY --from=build /app/build/libs/auth.jar auth.jar
USER app
# Small free instances have about 512 MB: let the JVM use most of it, with the lightest garbage collector.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "auth.jar"]
