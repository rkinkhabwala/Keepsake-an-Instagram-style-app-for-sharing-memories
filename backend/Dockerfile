FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
# libheif's heif-dec decodes HEIC uploads; the libde265 plugin supplies the HEVC codec.
RUN apt-get update \
    && apt-get install -y --no-install-recommends libheif-examples libheif-plugin-libde265 \
    && rm -rf /var/lib/apt/lists/* \
    && command -v heif-dec
WORKDIR /app
COPY --from=build /workspace/target/keepsake-backend-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
