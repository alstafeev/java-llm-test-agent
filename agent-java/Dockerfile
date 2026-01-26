FROM mcr.microsoft.com/playwright/java:v1.49.0-jammy

# Install Java 21
RUN apt-get update && apt-get install -y openjdk-21-jre-headless && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY target/agent-java-0.1.0-SNAPSHOT.jar app.jar

# Expose port
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
