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

# Copy the JAR built in Stage 1
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","app.jar"]