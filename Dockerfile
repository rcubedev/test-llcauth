# syntax=docker/dockerfile:1

# the native image is built in github actions
FROM ubuntu:26.04
WORKDIR /app
RUN useradd --system --uid 10001 app
COPY --chmod=755 build/native/nativeCompile/auth auth
USER app
EXPOSE 8080
ENTRYPOINT ["./auth"]
