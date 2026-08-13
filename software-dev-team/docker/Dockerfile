# Multi-stage build for sdlc-app.
# Runtime stays on JDK 26 (not JRE): nested Gradle in workspace/ needs javac.
# Also installs git (LocalGit) and Node (users-service-node tests).
#
# Gradle is installed with curl (retries) instead of ./gradlew. The wrapper's
# Java HTTP client often gets "Unexpected end of file" from services.gradle.org
# inside Docker Desktop.
#
#   docker compose up --build

ARG JDK_IMAGE=eclipse-temurin:26-jdk
ARG GRADLE_VERSION=9.4.1

FROM ${JDK_IMAGE} AS build
ARG GRADLE_VERSION
WORKDIR /workspace

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl unzip \
    && rm -rf /var/lib/apt/lists/* \
    && curl -fsSL --retry 8 --retry-all-errors --retry-delay 3 --connect-timeout 30 \
         -o /tmp/gradle.zip \
         "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
    && unzip -q /tmp/gradle.zip -d /opt \
    && rm /tmp/gradle.zip \
    && ln -s "/opt/gradle-${GRADLE_VERSION}/bin/gradle" /usr/local/bin/gradle

COPY settings.gradle build.gradle gradle.properties ./
COPY config/checkstyle ./config/checkstyle
COPY sdlc-core ./sdlc-core
COPY sdlc-app ./sdlc-app

RUN --mount=type=cache,target=/root/.gradle \
    gradle :sdlc-app:bootJar --no-daemon -x test \
    && BOOT_JAR="$(find sdlc-app/build/libs -name 'sdlc-app-*.jar' ! -name '*-plain.jar' | head -n 1)" \
    && test -n "$BOOT_JAR" \
    && cp "$BOOT_JAR" /workspace/app.jar

FROM ${JDK_IMAGE} AS runtime
WORKDIR /app

USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl git nodejs npm \
    && rm -rf /var/lib/apt/lists/* \
    && git config --system --add safe.directory '*'

ENV SDLC_HOME=/data \
    SERVER_PORT=8095

COPY --from=build /workspace/app.jar /app/app.jar

EXPOSE 8095

HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=12 \
    CMD curl -fsS "http://localhost:${SERVER_PORT}/actuator/health" || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "/app/app.jar"]
