# syntax=docker/dockerfile:1

# the native image is built in github actions, gzipped to fit vercel's 100 MB upload limit
FROM ubuntu:26.04 AS unpack
COPY build/native/nativeCompile/auth.gz /auth.gz
RUN gunzip /auth.gz && chmod 755 /auth

FROM ubuntu:26.04
WORKDIR /app
RUN useradd --system --uid 10001 app
COPY --from=unpack /auth auth
USER app
EXPOSE 8080
ENTRYPOINT ["./auth"]
