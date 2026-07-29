# Multi-stage build for a single Spring Boot module in this repo.
# Usage:
#   docker build --build-arg MODULE=gateway -f docker/Dockerfile .
#   docker build --build-arg MODULE=example-recruiting-api -f docker/Dockerfile .
#   docker build --build-arg MODULE=example-mcp-client -f docker/Dockerfile .

ARG JDK_IMAGE=eclipse-temurin:26-jdk
ARG JRE_IMAGE=eclipse-temurin:26-jre

FROM ${JDK_IMAGE} AS build
WORKDIR /workspace

ARG MODULE=gateway

COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle
COPY connectors ./connectors
COPY gateway ./gateway
COPY integrations ./integrations
COPY samples ./samples
COPY tests ./tests

# Prefer the named module boot JAR. A naive find of */build/libs/*.jar can pick
# the root aggregator or a connector library JAR (no Main-Class) first.
RUN chmod +x gradlew \
    && ./gradlew ":${MODULE}:bootJar" --no-daemon -x test \
    && BOOT_JAR="$(find . -name "${MODULE}-*.jar" ! -name '*-plain.jar' | head -n 1)" \
    && test -n "$BOOT_JAR" \
    && echo "Packaging boot JAR: ${BOOT_JAR}" \
    && cp "$BOOT_JAR" /workspace/app.jar

FROM ${JRE_IMAGE} AS runtime
WORKDIR /app

USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app app

USER app

COPY --from=build /workspace/app.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "/app/app.jar"]
