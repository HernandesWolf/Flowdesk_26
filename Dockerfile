# ---- Build stage ---------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
# Resolve dependencies first so this layer is cached until pom.xml changes
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- Runtime stage -------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S flowdesk && adduser -S flowdesk -G flowdesk \
    && mkdir -p /data/attachments && chown -R flowdesk:flowdesk /data
WORKDIR /app
COPY --from=build /workspace/target/flowdesk-*.jar app.jar
USER flowdesk
ENV STORAGE_PATH=/data/attachments \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
