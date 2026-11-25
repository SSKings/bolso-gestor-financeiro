# ---------- build ----------
# Imagens fixadas por digest (a tag pode ser republicada; o digest não). O Dependabot atualiza.
FROM eclipse-temurin:17-jdk-alpine@sha256:c4ca466f9ff25bdb4d28ff87199655c027d37b00226469e61d53f5fa11aae873 AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
# -C: checksum de cada artefato baixado precisa conferir
RUN ./mvnw -q -B -C dependency:go-offline
COPY src src
RUN ./mvnw -q -B -C -DskipTests package && cp target/bolso-*.jar /app.jar

# ---------- runtime: sem root ----------
FROM eclipse-temurin:17-jre-alpine@sha256:2b4113ae80e31f103b284dd3bc3e008add670cf8555417b5b289fa1128f2f0a3
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
