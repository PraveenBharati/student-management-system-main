# ===== STAGE 1: Build the JAR =====
# We use maven image to compile and package the app
FROM maven:3.9.5-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom.xml first - Docker caches this layer
# So if only source code changes, Maven won't re-download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Now copy the source code and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# ===== STAGE 2: Run the JAR =====
# We use a smaller JRE-only image (not the full JDK)
# This makes the final image much smaller (~200MB vs ~600MB)
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create a non-root user for security (production best practice)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy only the built JAR from Stage 1
COPY --from=builder /app/target/student-management-*.jar app.jar

# Change ownership to non-root user
RUN chown appuser:appgroup app.jar

# Switch to non-root user
USER appuser

# Expose the port Spring Boot runs on
EXPOSE 8080

# Health check - Docker/Kubernetes will use this
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
