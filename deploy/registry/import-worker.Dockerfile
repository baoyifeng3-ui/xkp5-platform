FROM debian:bookworm-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates openjdk-17-jre-headless skopeo \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /app /data/registry-import /data/registry-staging

COPY java/match-mgr/target/*.jar /app/match-mgr.jar

ENV SPRING_MAIN_WEB_APPLICATION_TYPE=none

# The importer uses skopeo against Registry v2 and never needs a Docker daemon socket.
ENTRYPOINT ["java", "-jar", "/app/match-mgr.jar"]
