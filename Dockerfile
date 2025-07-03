# ----------- Build Stage -------------
FROM gradle:8.5-jdk21 AS build

WORKDIR /app

# Copy Gradle wrapper & project setup files first (to leverage Docker cache for dependencies)
COPY app/build.gradle settings.gradle gradle.properties /app/
COPY gradle /app/gradle

# Pre-download dependencies
RUN gradle build -x test --no-daemon || return 0

# Now copy the source
COPY app/src /app/src

# Build the Spring Boot fat JAR
RUN gradle bootJar -x test --no-daemon

# ----------- Run Stage -------------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the built jar from the build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Expose app port (optional, for documentation)
EXPOSE 8080

# Start app
ENTRYPOINT ["java","-jar","/app/app.jar"]
