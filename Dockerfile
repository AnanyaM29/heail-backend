# ---------- Stage 1: Build the application ----------
FROM gradle:8.14.3-jdk21 AS builder

WORKDIR /app

# Copy everything
COPY . .

# Build the Spring Boot executable JAR
RUN gradle bootJar --no-daemon

# ---------- Stage 2: Run the application ----------
FROM eclipse-temurin:21-jre

WORKDIR /app

# curl needed only for the HEALTHCHECK below
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Copy the JAR built in Stage 1
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java","-jar","app.jar"]