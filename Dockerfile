# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY . .

# tests run in github actions
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon --console=plain

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 app
COPY --from=build /app/build/libs/auth.jar auth.jar
USER app
# small free instances have about 512 MB: let the jvm use most of it, with the lightest garbage collector
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "auth.jar"]
