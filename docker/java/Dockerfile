FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY java/match-mgr java/match-mgr
RUN --mount=type=cache,id=match-maven-repository,target=/root/.m2 \
  mvn -f java/match-mgr/pom.xml package -DskipTests

FROM python:3.11-slim-bookworm
ENV LANG=C.UTF-8 TZ=Asia/Shanghai
RUN apt-get update \
  && (apt-get install -y --no-install-recommends openjdk-17-jre-headless \
      || apt-get install -y --no-install-recommends openjdk-21-jre-headless) \
  && pip install --no-cache-dir numpy \
  && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/java/match-mgr/target/match-mgr-1.0-SNAPSHOT.jar /app/app.jar
COPY python /app/python
EXPOSE 19141
CMD ["java", "--add-opens=java.base/java.lang=ALL-UNNAMED", "-jar", "/app/app.jar"]
