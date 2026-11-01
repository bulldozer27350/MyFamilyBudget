# syntax=docker/dockerfile:1

# =============================================================================
# Etape 1 : Build - compile le backend Spring Boot et embarque le frontend
# =============================================================================
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Copie des pom.xml en premier pour profiter du cache de couche Docker
# (back/pom.xml = parent Maven du reactor, requis pour résoudre les modules back/*/pom.xml)
COPY back/pom.xml back/pom.xml
COPY back/myfamilybudget-bom/pom.xml back/myfamilybudget-bom/pom.xml
COPY back/api/pom.xml back/api/pom.xml
COPY back/infra-jpa/pom.xml back/infra-jpa/pom.xml
COPY back/application-api/pom.xml back/application-api/pom.xml
COPY back/application/pom.xml back/application/pom.xml
COPY back/web/pom.xml back/web/pom.xml
COPY back/bootstrap/pom.xml back/bootstrap/pom.xml

# Telechargement des dependances en s'appuyant sur le cache persistant .m2
# -DexcludeGroupIds=com.moe.myfamilybudget ne filtre que le telechargement des jars portant ce
# groupId (dont les *-api/*-core de tous les silos extraits, memes groupId bien qu'externes) :
# la lecture du descripteur POM de chaque dependance declaree (necessaire pour construire
# l'arbre de dependances) a lieu AVANT ce filtre et s'applique donc aussi a ces silos.
# Cette etape necessite donc elle aussi une authentification sur chaque domain-<nom>-packages.
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=secret,id=maven_settings,target=/root/.m2/settings.xml,required=true \
    mvn -f back/pom.xml -q -pl bootstrap -am dependency:go-offline -DexcludeGroupIds=com.moe.myfamilybudget

# Copie du reste du projet
# Les *-api/*-core des 10 silos extraits (credit, retirement, tax, wealth, bank-pointage, treasury,
# analysis, goals, settings, notifications, market) ne sont plus dans ce depot : ils sont desormais
# resolus comme dependances Maven depuis GitHub Packages a l'etape de compilation, et non plus
# copies en source.
COPY openapi.yaml openapi.yaml
COPY openapi openapi
COPY view view
COPY back/myfamilybudget-bom back/myfamilybudget-bom
COPY back/api back/api
COPY back/infra-jpa back/infra-jpa
COPY back/application-api back/application-api
COPY back/application back/application
COPY back/web back/web
COPY back/bootstrap back/bootstrap

# Compilation du JAR executable avec réutilisation du cache .m2.
# Le reactor resout ici les *-api/*-core des 10 silos extraits (groupId com.moe.myfamilybudget,
# mais un depot GitHub Packages "domain-<nom>-packages" par silo, declare dans back/pom.xml) :
# chacun exige une authentification meme en lecture. Le settings.xml porteur des tokens est monte
# via un secret BuildKit (jamais copie dans une couche de l'image) ; voir
# docker/maven-settings.xml.example et le job build-and-push de .github/workflows/ci-cd.yml pour
# sa fourniture en CI/local.
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=secret,id=maven_settings,target=/root/.m2/settings.xml,required=true \
    mvn -f back/pom.xml -q -pl bootstrap -am clean package -DskipTests -Dassembly.skipAssembly=true

# =============================================================================
# Etape 2 : Runtime - image finale, legere, sans outils de build
# =============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S myfamilybudget && adduser -S myfamilybudget -G myfamilybudget

WORKDIR /app

# Utilisation du pattern *.jar pour eviter de hardcoder la version (ex: 1.0.0-SNAPSHOT)
COPY --from=build /workspace/back/bootstrap/target/*.jar /app/app.jar

RUN chown -R myfamilybudget:myfamilybudget /app
USER myfamilybudget

ENV SPRING_PROFILES_ACTIVE=docker

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=5 \
    CMD wget -qO- http://localhost:8080/myfamilybudget/index.html >/dev/null 2>&1 || exit 1

ENTRYPOINT ["java", "-Xms64m", "-Xmx512m", "-jar", "/app/app.jar"]