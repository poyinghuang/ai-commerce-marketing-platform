FROM maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw --batch-mode dependency:go-offline
COPY src src
RUN ./mvnw --batch-mode package -DskipTests

FROM eclipse-temurin:21.0.9_10-jre@sha256:d45e4719da1774b3abde398867f78a6d9b9afe4265f574f3d54062f6daeb00b3 AS runtime
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/marketing-platform-backend-0.0.1-SNAPSHOT.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
