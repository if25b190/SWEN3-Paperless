# Build stage
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

# Copy Gradle wrapper and build files to leverage layer caching
COPY gradle/ gradle/
COPY gradlew settings.gradle.kts build.gradle.kts ./
RUN chmod +x ./gradlew && ./gradlew dependencies --no-daemon || true

# Copy source code and build bootJar
COPY openapi-templates/ openapi-templates/
COPY src/ src/
RUN ./gradlew bootJar -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:25-jre AS runner
WORKDIR /app

# OpenTelemetry Java agent: zero-code distributed tracing (HTTP, JDBC,
# RabbitMQ publish/consume, trace ids in logback MDC), exported as OTLP via
# the OTEL_* environment variables (see docker-compose.yml).
ARG OTEL_AGENT_VERSION=2.33.1
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /opt/otel \
    && curl -fsSL -o /opt/otel/opentelemetry-javaagent.jar \
       "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar"

# Security: run as non-root user
RUN groupadd -r spring && useradd -r -g spring spring \
    && mkdir -p /tmp/paperless-documents \
    && chown spring:spring /tmp/paperless-documents
USER spring:spring

# Copy built artifact from builder stage (Gradle outputs to build/libs)
COPY --from=builder /app/build/libs/*.jar app.jar

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
