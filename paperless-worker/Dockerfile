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
# OCR toolchain: Tesseract with English language data and Ghostscript for
# PDF rasterization. Both must stay on the worker's PATH.
RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng ghostscript \
    && rm -rf /var/lib/apt/lists/*
# Security: run as non-root user
RUN groupadd -r spring && useradd -r -g spring spring \
    && mkdir -p /tmp/paperless-documents \
    && chown spring:spring /tmp/paperless-documents
USER spring:spring
# Copy built artifact from builder stage (Gradle outputs to build/libs)
COPY --from=builder /app/build/libs/*.jar app.jar
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
