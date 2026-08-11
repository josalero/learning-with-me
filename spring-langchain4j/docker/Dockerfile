# Multi-stage build for a Spring Boot module in this repo.
# Usage:
#   docker build --build-arg MODULE=spring-ai-app -f docker/Dockerfile .
#   docker build --build-arg MODULE=langchain4j-app -f docker/Dockerfile .

ARG JDK_IMAGE=eclipse-temurin:26-jdk
ARG JRE_IMAGE=eclipse-temurin:26-jre

FROM ${JDK_IMAGE} AS build
WORKDIR /workspace

ARG MODULE=spring-ai-app

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY shared-prompts ./shared-prompts
COPY spring-ai-app ./spring-ai-app
COPY langchain4j-app ./langchain4j-app

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
