# ----------- Build Stage -------------
FROM gradle:8.5-jdk21 AS build

WORKDIR /app

# Copy Gradle wrapper & project setup files first (to leverage Docker cache for dependencies)
COPY app/build.gradle settings.gradle gradle.properties /app/
COPY gradle /app/gradle

# Pre-download dependencies
RUN gradle build -x test --no-daemon || return 0

# Now copy the source (which includes src/main/resources/application.yml)
COPY app/src /app/src

# Build the Spring Boot fat JAR
RUN gradle bootJar -x test --no-daemon

# ----------- Run Stage -------------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the built jar from the build stage
COPY --from=build /app/build/libs/*.jar app.jar

# IMPORTANT: Create the directory for SQLite database
# Note: This means the SQLite database will be ephemeral and data will be lost
# when the container is removed or restarted without a volume.
RUN mkdir -p /app/data

# Expose app port
EXPOSE 8081

# Start app with the ability to override config externally
ENTRYPOINT ["java", "-jar", "/app/app.jar"]