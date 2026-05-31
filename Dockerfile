# syntax=docker/dockerfile:1.7@sha256:a57df69d0ea827fb7266491f2813635de6f17269be881f696fbfdf2d83dda33e

FROM cgr.dev/chainguard/jre:latest@sha256:11ddb6ab37678eaf5fabeda23a9f50e2b338be0cf64fca5b0fc6d424a07366ba
WORKDIR /app

ARG BUILD_DATE
ARG BUILD_VERSION
ARG BUILD_REVISION

LABEL org.opencontainers.image.title="Kairos" \
      org.opencontainers.image.description="Self-hosted uptime and availability monitoring application" \
      org.opencontainers.image.url="https://github.com/wenisch-tech/Kairos" \
      org.opencontainers.image.source="https://github.com/wenisch-tech/Kairos" \
      org.opencontainers.image.documentation="https://github.com/wenisch-tech/Kairos/tree/main/docs" \
      org.opencontainers.image.authors="JFWenisch" \
      org.opencontainers.image.licenses="GPL-3.0" \
      org.opencontainers.image.vendor="JFWenisch" \
      org.opencontainers.image.version="${BUILD_VERSION}" \
      org.opencontainers.image.revision="${BUILD_REVISION}" \
      org.opencontainers.image.created="${BUILD_DATE}"



# Runtime-only image: Docker/OCI checks use registry HTTPS APIs.
# No Docker daemon, Docker CLI, or Podman tooling is included.
ENV DOCKER_HOST=unix:///dev/null
ENV CONTAINERS_CONF=/dev/null
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=20.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/urandom"

COPY target/kairos-*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
