# syntax=docker/dockerfile:1

# the jar is built and tested in github actions (or by ./gradlew bootJar locally)
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 app
COPY build/libs/auth.jar auth.jar
USER app
# vercel hobby functions get 2 GB and 1 vCPU: let the jvm use most of it, with the lightest garbage collector
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "auth.jar"]
