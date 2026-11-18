# ---------- build ----------
# Imagens fixadas por digest (a tag pode ser republicada; o digest não). O Dependabot atualiza.
FROM eclipse-temurin:17-jdk-alpine@sha256:efefbce46b0d79073a3a676d91ac6c4bf370d348af18e388bbf7482543d7866a AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
# -C: checksum de cada artefato baixado precisa conferir
RUN ./mvnw -q -B -C dependency:go-offline
COPY src src
RUN ./mvnw -q -B -C -DskipTests package && cp target/bolso-*.jar /app.jar

# ---------- runtime: sem root ----------
FROM eclipse-temurin:17-jre-alpine@sha256:e2ba4e947bd5e236a9866fbdda03e4936036cb9e9d16ea0d84d103d6634a5c21
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build --chown=app:app /app.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
