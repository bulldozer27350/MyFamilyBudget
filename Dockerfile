# syntax=docker/dockerfile:1

# =============================================================================
# Etape 1 : Build - compile le backend Spring Boot et embarque le frontend
# =============================================================================
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Copie des pom.xml en premier pour profiter du cache de couche Docker
# (back/pom.xml = parent Maven du reactor, requis pour résoudre les modules back/*/pom.xml)
COPY back/pom.xml back/pom.xml
COPY back/retirement-api/pom.xml back/retirement-api/pom.xml
COPY back/retirement-core/pom.xml back/retirement-core/pom.xml
COPY back/tax-api/pom.xml back/tax-api/pom.xml
COPY back/tax-core/pom.xml back/tax-core/pom.xml
COPY back/wealth-api/pom.xml back/wealth-api/pom.xml
COPY back/wealth-core/pom.xml back/wealth-core/pom.xml
COPY back/bank-pointage-api/pom.xml back/bank-pointage-api/pom.xml
COPY back/bank-pointage-core/pom.xml back/bank-pointage-core/pom.xml
COPY back/treasury-api/pom.xml back/treasury-api/pom.xml
COPY back/treasury-core/pom.xml back/treasury-core/pom.xml
COPY back/analysis-api/pom.xml back/analysis-api/pom.xml
COPY back/analysis-core/pom.xml back/analysis-core/pom.xml
COPY back/credit-api/pom.xml back/credit-api/pom.xml
COPY back/credit-core/pom.xml back/credit-core/pom.xml
COPY back/goals-api/pom.xml back/goals-api/pom.xml
COPY back/notifications-api/pom.xml back/notifications-api/pom.xml
COPY back/notifications-core/pom.xml back/notifications-core/pom.xml
COPY back/market-api/pom.xml back/market-api/pom.xml
COPY back/market-core/pom.xml back/market-core/pom.xml
COPY back/api/pom.xml back/api/pom.xml
COPY back/infra-jpa/pom.xml back/infra-jpa/pom.xml
COPY back/transition-snapshot/pom.xml back/transition-snapshot/pom.xml
COPY back/application/pom.xml back/application/pom.xml
COPY back/persistence/pom.xml back/persistence/pom.xml
COPY back/server/pom.xml back/server/pom.xml

# Telechargement des dependances en s'appuyant sur le cache persistant .m2
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f back/pom.xml -q -pl server -am dependency:go-offline -DexcludeGroupIds=com.moe.myfamilybudget

# Copie du reste du projet
COPY openapi.yaml openapi.yaml
COPY openapi openapi
COPY view view
COPY back/retirement-api back/retirement-api
COPY back/retirement-core back/retirement-core
COPY back/tax-api back/tax-api
COPY back/tax-core back/tax-core
COPY back/wealth-api back/wealth-api
COPY back/wealth-core back/wealth-core
COPY back/bank-pointage-api back/bank-pointage-api
COPY back/bank-pointage-core back/bank-pointage-core
COPY back/treasury-api back/treasury-api
COPY back/treasury-core back/treasury-core
COPY back/analysis-api back/analysis-api
COPY back/analysis-core back/analysis-core
COPY back/credit-api back/credit-api
COPY back/credit-core back/credit-core
COPY back/goals-api back/goals-api
COPY back/notifications-api back/notifications-api
COPY back/notifications-core back/notifications-core
COPY back/market-api back/market-api
COPY back/market-core back/market-core
COPY back/api back/api
COPY back/infra-jpa back/infra-jpa
COPY back/transition-snapshot back/transition-snapshot
COPY back/application back/application
COPY back/persistence back/persistence
COPY back/server back/server

# Compilation du JAR executable avec réutilisation du cache .m2
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f back/pom.xml -q -pl server -am clean package -DskipTests -Dassembly.skipAssembly=true

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