# syntax=docker/dockerfile:1

# ---- Build stage ------------------------------------------------------------
FROM gradle:8.14-jdk21 AS build
WORKDIR /workspace

# Resolve dependencies first so they're cached independently of source changes.
COPY settings.gradle.kts build.gradle.kts ./
RUN gradle --no-daemon dependencies > /dev/null

COPY src ./src
RUN gradle --no-daemon bootJar


# ---- Java agent download stage ----------------------------------------------------------
FROM curlimages/curl:latest AS ddagent
ARG DD_JAVA_AGENT_URL=https://dtdg.co/latest-java-tracer
RUN curl -fsSL --retry 3 -o /tmp/dd-java-agent.jar "${DD_JAVA_AGENT_URL}" && size=$(stat -c %s /tmp/dd-java-agent.jar) && echo "dd-java-agent.jar downloaded: ${size} bytes" && [ "${size}" -gt 1000000 ] || { echo "ERROR: download is not a valid tracer JAR"; head -c 300 /tmp/dd-java-agent.jar; exit 1; }



# ---- Runtime stage ----------------------------------------------------------
FROM eclipse-temurin:21-jre

# Datadog Java tracer. Pin a version for reproducible builds, e.g.:
#   --build-arg DD_JAVA_AGENT_URL=https://repo1.maven.org/maven2/com/datadoghq/dd-java-agent/<ver>/dd-java-agent-<ver>.jar

COPY --from=ddagent /tmp/dd-java-agent.jar /opt/datadog/dd-java-agent.jar
#ARG DD_JAVA_AGENT_URL=https://dtdg.co/latest-java-tracer
#ADD --chmod=644 ${DD_JAVA_AGENT_URL} /opt/datadog/dd-java-agent.jar

RUN useradd --system --uid 1001 --no-create-home app
WORKDIR /app
COPY --from=build --chown=app /workspace/build/libs/app.jar /app/app.jar
USER app

RUN DD_TRACE_ENABLED=false java -javaagent:/opt/datadog/dd-java-agent.jar -version

EXPOSE 8080
# The tracer is attached via JAVA_TOOL_OPTIONS (set in docker-compose.yml), so the
# image also runs untraced if that variable is left out.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
