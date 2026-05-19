# Build stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# 1. Copy the pom.xml first
COPY pom.xml .

# 2. Download all dependencies
RUN mvn dependency:go-offline -B

# 3. Copy the source code
COPY src ./src

# 4. Build the application offline
RUN mvn clean package -DskipTests -o

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create a non-root user and group
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy the built jar from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose the internal application port (Documentation only)
EXPOSE 8082

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]