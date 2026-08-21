FROM debian:bookworm-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates openjdk-17-jre-headless skopeo \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 xkp \
    && useradd --system --uid 10001 --gid 10001 --home-dir /app --shell /usr/sbin/nologin xkp \
    && mkdir -p /app /data/registry-import /data/registry-staging \
    && chown -R xkp:xkp /app /data/registry-import /data/registry-staging

COPY java/match-mgr/target/*.jar /app/match-mgr.jar

ENV SPRING_MAIN_WEB_APPLICATION_TYPE=none
ENV XKP_REGISTRY_IMPORT_ENABLED=true

USER 10001:10001

# The importer uses skopeo against Registry v2 and never needs a Docker daemon socket.
ENTRYPOINT ["java", "-jar", "/app/match-mgr.jar"]
