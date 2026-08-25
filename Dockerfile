# Multi-stage build: the JDK, Maven wrapper download, and full source tree never make it into the
# final image -- only the built jar and a JRE do. See docs/deployment.md for the reasoning behind
# each choice below.

# --- Build stage ---
# BellSoft Liberica, not Eclipse Temurin: this project targets Java 26 (see pom.xml), and Liberica
# is the vendor already used for local development (see CLAUDE.md's JAVA_HOME) -- same vendor in
# dev and CI/prod avoids any "works on my machine" vendor-specific JVM behavior difference.
FROM bellsoft/liberica-openjdk-alpine:26 AS build
WORKDIR /app

# Dependencies first, on their own layer, so a source-only change doesn't invalidate the (slow)
# dependency-download layer on the next build.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B

COPY src ./src
# Tests already ran as their own CI step before the image build ever starts (see
# .github/workflows/ci.yml) -- running the full Postgres-backed integration suite again here
# would just fail anyway, since this build stage has no database to connect to.
RUN ./mvnw clean package -DskipTests -B

# --- Runtime stage ---
FROM bellsoft/liberica-openjre-alpine:26
WORKDIR /app

# Runs as an unprivileged user rather than root, matching the principle of least privilege for
# anything handling network input.
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/*.jar app.jar
RUN chown -R app:app /app
USER app

# Render (and most platforms) inject PORT at runtime and route traffic to whatever it's set to;
# server.port=${PORT:8080} in application.properties falls back to 8080 for `docker run` without
# PORT set (e.g. local testing). EXPOSE is documentation for the latter case -- it has no effect
# on what Render actually does with PORT.
EXPOSE 8080

# Uses the actuator health endpoint added for Phase 8 (see SecurityConfig for why it's the one
# unauthenticated path) -- lets `docker run` and any platform that reads Docker's own HEALTHCHECK
# (Render uses its own dashboard-configured health check path instead, but this keeps the image
# correct and testable standalone).
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -q -O /dev/null http://localhost:${PORT:-8080}/actuator/health || exit 1

# JAVA_OPTS is empty by default and left overridable via an env var (e.g. to tune GC/heap on a
# memory-constrained free-tier instance) without rebuilding the image. MaxRAMPercentage rather
# than a fixed -Xmx: the container-aware default lets the JVM size its heap relative to whatever
# memory limit the platform actually assigns, instead of a number picked blind at build time.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
