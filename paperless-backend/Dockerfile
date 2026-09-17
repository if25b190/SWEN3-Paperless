# Build stage
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

# Copy Gradle wrapper and build files to leverage layer caching
COPY gradle/ gradle/
COPY gradlew settings.gradle.kts build.gradle.kts ./
RUN chmod +x ./gradlew && ./gradlew dependencies --no-daemon || true

# Copy source code and build bootJar
COPY src/ src/
RUN ./gradlew bootJar -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:25-jre AS runner
WORKDIR /app

# Security: run as non-root user
RUN groupadd -r spring && useradd -r -g spring spring
USER spring:spring

# Copy built artifact from builder stage (Gradle outputs to build/libs)
COPY --from=builder /app/build/libs/*.jar app.jar

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
