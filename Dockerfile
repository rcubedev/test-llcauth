# syntax=docker/dockerfile:1

# the jar is built and tested in github actions (or by ./gradlew bootJar locally)
FROM eclipse-temurin:25-jre AS app
COPY build/libs/auth.jar /tmp/auth.jar
RUN java -Djarmode=tools -jar /tmp/auth.jar extract --destination /app

# start the app once against a throwaway database to record an AOT cache
FROM eclipse-temurin:25-jre AS training
RUN apt-get update \
 && apt-get install -y --no-install-recommends postgresql \
 && rm -rf /var/lib/apt/lists/*
COPY --from=app /app /app
WORKDIR /app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"
RUN pg_ctlcluster "$(ls /etc/postgresql)" main start \
 && su postgres -c "psql -q -c \"ALTER USER postgres PASSWORD 'training'\"" \
 && java -XX:AOTCacheOutput=app.aot -Xlog:aot=error -Dspring.context.exit=onRefresh \
      -Dspring.datasource.url=jdbc:postgresql://localhost/postgres \
      -Dspring.datasource.username=postgres -Dspring.datasource.password=training \
      -Dauth.oauth.generate-jwt-key=true -Dauth.mail.resend-api-key=training \
      -jar auth.jar

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 app
COPY --from=app /app /app
COPY --from=training /app/app.aot /app/app.aot
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"
EXPOSE 8080
ENTRYPOINT ["java", "-XX:AOTCache=app.aot", "-jar", "auth.jar"]
