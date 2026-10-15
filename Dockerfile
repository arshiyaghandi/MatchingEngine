# ═══════════════════════════════════════════════════════════
# Matching Engine — Multi-stage Docker Build
# ═══════════════════════════════════════════════════════════

# --- Stage 1: Build ---
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .
RUN chmod +x mvnw && ./mvnw dependency:resolve -B 2>/dev/null || true

COPY src/ src/
# Use Maven wrapper if present, otherwise fall back to mvn
RUN if [ -f mvnw ]; then ./mvnw clean package -DskipTests -B; else mvn clean package -DskipTests -B; fi

# --- Stage 2: Runtime ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /app/target/matching-engine-*.jar app.jar

# Expose application port and actuator
EXPOSE 8080

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseZGC"

USER appuser

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
