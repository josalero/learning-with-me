# syntax=docker/dockerfile:1

FROM eclipse-temurin:26-jdk-jammy AS build
WORKDIR /workspace

RUN apt-get update \
	&& apt-get install -y --no-install-recommends maven \
	&& rm -rf /var/lib/apt/lists/*

COPY pom.xml .
COPY skills-agent/pom.xml skills-agent/
COPY screening-orchestrator/pom.xml screening-orchestrator/
COPY skills-agent/src skills-agent/src
COPY screening-orchestrator/src screening-orchestrator/src

RUN mvn -B -DskipTests package

FROM eclipse-temurin:26-jre-jammy AS skills-agent
WORKDIR /app
RUN apt-get update \
	&& apt-get install -y --no-install-recommends curl \
	&& rm -rf /var/lib/apt/lists/* \
	&& useradd --system --uid 10001 --create-home appuser
COPY --from=build /workspace/skills-agent/target/skills-agent-*.jar /app/app.jar
USER 10001
EXPOSE 8081
HEALTHCHECK --interval=5s --timeout=5s --start-period=40s --retries=18 \
	CMD curl -fsS http://127.0.0.1:8081/a2a/.well-known/agent-card.json >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM eclipse-temurin:26-jre-jammy AS screening-orchestrator
WORKDIR /app
RUN apt-get update \
	&& apt-get install -y --no-install-recommends curl \
	&& rm -rf /var/lib/apt/lists/* \
	&& useradd --system --uid 10001 --create-home appuser
COPY --from=build /workspace/screening-orchestrator/target/screening-orchestrator-*.jar /app/app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
