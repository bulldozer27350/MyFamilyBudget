# syntax=docker/dockerfile:1

# =============================================================================
# Etape 1 : Build - compile le backend Spring Boot et embarque le frontend
# =============================================================================
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Copie du pom.xml en premier pour profiter du cache Docker sur les dependances
COPY back/server/pom.xml back/server/pom.xml
RUN mvn -f back/server/pom.xml -q dependency:go-offline

# Copie du reste du projet (le pom.xml reference ../../view et ../../openapi.yaml)
COPY openapi.yaml openapi.yaml
COPY view view
COPY back/server back/server

# Build du jar executable (frontend embarque via maven-resources-plugin)
# -Dassembly.skipAssembly=true : on n'a pas besoin du zip/tar.gz portable dans l'image
RUN mvn -f back/server/pom.xml -q clean package -DskipTests -Dassembly.skipAssembly=true

# =============================================================================
# Etape 2 : Runtime - image finale, legere, sans outils de build
# =============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S myfamilybudget && adduser -S myfamilybudget -G myfamilybudget

WORKDIR /app

COPY --from=build /workspace/back/server/target/server-1.0.0-SNAPSHOT.jar /app/app.jar

RUN chown -R myfamilybudget:myfamilybudget /app
USER myfamilybudget

# Le jar embarque a la fois les drivers H2 (profil par defaut) et PostgreSQL.
# L'image Docker est destinee au deploiement conteneurise : le profil "docker"
# (application-docker.yml, datasource PostgreSQL) est donc active par defaut ici.
# Peut etre surcharge au besoin via -e SPRING_PROFILES_ACTIVE=xxx.
ENV SPRING_PROFILES_ACTIVE=docker

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=5 \
    CMD wget -qO- http://localhost:8080/api/v1/index.html >/dev/null 2>&1 || exit 1

ENTRYPOINT ["java", "-Xms64m", "-Xmx512m", "-jar", "/app/app.jar"]
