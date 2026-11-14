# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# reused until the build files change
COPY gradlew gradle.properties settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY build-logic build-logic
RUN ./gradlew tailwindInstall --no-daemon --console=plain

# tests run in github actions
COPY src src
RUN ./gradlew bootJar --no-daemon --console=plain

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 app
COPY --from=build /app/build/libs/auth.jar auth.jar
USER app
# small free instances have about 512 MB: let the jvm use most of it, with the lightest garbage collector
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "auth.jar"]
