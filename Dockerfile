# syntax=docker/dockerfile:1

# =============================================================================
# Etape 1 : Build - compile le backend Spring Boot et embarque le frontend
# =============================================================================
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Copie du pom.xml en premier pour profiter du cache de couche Docker
COPY back/server/pom.xml back/server/pom.xml

# Telechargement des dependances en s'appuyant sur le cache persistant .m2
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f back/server/pom.xml -q dependency:go-offline

# Copie du reste du projet
COPY openapi.yaml openapi.yaml
COPY view view
COPY back/server back/server

# Compilation du JAR executable avec réutilisation du cache .m2
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f back/server/pom.xml -q clean package -DskipTests -Dassembly.skipAssembly=true

# =============================================================================
# Etape 2 : Runtime - image finale, legere, sans outils de build
# =============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S myfamilybudget && adduser -S myfamilybudget -G myfamilybudget

WORKDIR /app

# Utilisation du pattern *.jar pour eviter de hardcoder la version (ex: 1.0.0-SNAPSHOT)
COPY --from=build /workspace/back/server/target/*.jar /app/app.jar

RUN chown -R myfamilybudget:myfamilybudget /app
USER myfamilybudget

ENV SPRING_PROFILES_ACTIVE=docker

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=5 \
    CMD wget -qO- http://localhost:8080/myfamilybudget/index.html >/dev/null 2>&1 || exit 1

ENTRYPOINT ["java", "-Xms64m", "-Xmx512m", "-jar", "/app/app.jar"]