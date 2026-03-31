# Stage 1: Build the application using Gradle
FROM gradle:8.5.0-jdk17 AS builder
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts /app/
COPY src /app/src/
RUN gradle installDist --no-daemon

# Stage 2: Run the application in a lightweight JRE image
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the built distribution from the builder stage
COPY --from=builder /app/build/install/ktor-push-service /app/

# Expose the port defined in application.conf
EXPOSE 8080

# Firebase credentials path for Render.com secret files (if configured)
ENV FIREBASE_CREDENTIALS_PATH=/etc/secrets/firebase-adminsdk.json

# Run the Ktor application
CMD ["/app/bin/ktor-push-service"]
