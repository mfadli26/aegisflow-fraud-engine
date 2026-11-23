# syntax=docker/dockerfile:1.7

# Stage 1: Multi-module Maven Build
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

COPY pom.xml .
COPY aegis-common/pom.xml aegis-common/
COPY transaction-service/pom.xml transaction-service/
COPY fraud-engine-service/pom.xml fraud-engine-service/
COPY investigation-service/pom.xml investigation-service/

COPY aegis-common/src aegis-common/src
COPY transaction-service/src transaction-service/src
COPY fraud-engine-service/src fraud-engine-service/src
COPY investigation-service/src investigation-service/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests -B

# Stage 2: Minimal Non-Root Runtime Image
FROM eclipse-temurin:21-jre-alpine
ARG SERVICE_NAME
WORKDIR /app

RUN addgroup -S aegis && adduser -S aegis -G aegis \
    && mkdir -p /tmp/aegisflow-kafka-streams \
    && chown -R aegis:aegis /app /tmp/aegisflow-kafka-streams

COPY --from=builder /workspace/${SERVICE_NAME}/target/${SERVICE_NAME}-1.0.0.jar /app/app.jar
USER aegis

ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx256m"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]