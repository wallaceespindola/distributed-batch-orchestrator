# syntax=docker/dockerfile:1

# ---- Stage 1: builder ----
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Dependency layer caching: resolve deps before copying sources
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B package -DskipTests

# ---- Stage 2: runtime ----
FROM eclipse-temurin:21-jre AS runtime

# eclipse-temurin JRE images are Ubuntu-based and ship neither curl nor wget;
# install curl for the HEALTHCHECK in a single layer.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN addgroup --system app && adduser --system --ingroup app app

WORKDIR /app
COPY --from=builder /build/target/distributed-batch-orchestrator-1.0.0.jar app.jar
RUN chown -R app:app /app

USER app

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
