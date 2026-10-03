# syntax=docker/dockerfile:1

# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests && cp target/*.jar app.jar

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app \
 && mkdir -p /app/data/spool /app/tmp \
 && chown -R 10001:10001 /app
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
USER 10001:10001

EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod \
    TELECOM_SPOOL_DIRECTORY=/app/data/spool \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Duser.timezone=UTC -Djava.io.tmpdir=/app/tmp"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
