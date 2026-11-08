# syntax=docker/dockerfile:1
# Stage 1: Build & Cache
FROM eclipse-temurin:21-jdk-noble AS builder
WORKDIR /build

ARG MODULE_NAME

COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY core-infrastructure/pom.xml core-infrastructure/
COPY insightrag-worker/pom.xml insightrag-worker/
COPY insightrag-ingestion/pom.xml insightrag-ingestion/
COPY insightrag-retrieval/pom.xml insightrag-retrieval/

# BuildKit cache to safely store .m2 dependencies across builds
# -B and -ntp to keep CI/CD logs clean
RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -ntp dependency:go-offline -pl ${MODULE_NAME} -am

COPY core-infrastructure/src core-infrastructure/src
COPY ${MODULE_NAME}/src ${MODULE_NAME}/src

RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -ntp clean package -pl ${MODULE_NAME} -am -DskipTests \
    && java -Djarmode=tools -jar ${MODULE_NAME}/target/*.jar extract --layers --launcher --destination /build/extracted

# Stage 2: Minimal Runtime
FROM eclipse-temurin:21-jre-noble
WORKDIR /app

RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --create-home --home-dir /home/app --shell /usr/sbin/nologin app

COPY --from=builder --chown=app:app /build/extracted/dependencies/ ./
COPY --from=builder --chown=app:app /build/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=app:app /build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=app:app /build/extracted/application/ ./

USER app

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8"

ARG SERVICE_PORT=8080
ENV HEALTHCHECK_URL=http://localhost:${SERVICE_PORT}/actuator/health/liveness
EXPOSE ${SERVICE_PORT}

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl --fail --no-progress-meter --show-error ${HEALTHCHECK_URL} || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]


