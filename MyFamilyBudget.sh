#!/usr/bin/env bash
# ==============================================================================
#  MyFamilyBudget - Lanceur Portable Autonome (Linux / macOS)
#  1. Ouvre instantanément le SplashScreen web local (0 seconde)
#  2. Lance le serveur Spring Boot avec la JRE embarquée ou système
# ==============================================================================

set -e

APP_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
APP_JAR="$APP_DIR/MyFamilyBudget.jar"
SPLASH_HTML="$APP_DIR/splashscreen.html"

if [ ! -f "$APP_JAR" ]; then
    APP_JAR="$APP_DIR/back/server/target/server-1.0.0-SNAPSHOT.jar"
fi

echo "=================================================================="
echo "            Lancement de MyFamilyBudget en cours...               "
echo "=================================================================="
echo "[INFO] Répertoire de l'application : $APP_DIR"

# 0. Ouverture instantanée du SplashScreen
if [ -f "$SPLASH_HTML" ]; then
    echo "[INFO] Ouverture immédiate du SplashScreen..."
    if [[ "$OSTYPE" == "darwin"* ]]; then
        open "$SPLASH_HTML" &
    elif command -v xdg-open >/dev/null 2>&1; then
        xdg-open "$SPLASH_HTML" >/dev/null 2>&1 &
    fi
fi

if [ -f "$APP_DIR/jre/bin/java" ]; then
    JAVA_CMD="$APP_DIR/jre/bin/java"
    echo "[INFO] Utilisation de la JRE embarquée (/jre)"
elif [ -f "$APP_DIR/runtime/bin/java" ]; then
    JAVA_CMD="$APP_DIR/runtime/bin/java"
    echo "[INFO] Utilisation de la JRE embarquée (/runtime)"
elif [ -n "$JAVA_HOME" ] && [ -f "$JAVA_HOME/bin/java" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
    echo "[INFO] Utilisation de JAVA_HOME : $JAVA_HOME"
elif command -v java >/dev/null 2>&1; then
    JAVA_CMD="java"
    echo "[INFO] Utilisation de la commande 'java' du PATH"
else
    echo ""
    echo "=================================================================="
    echo " [ERREUR] AUCUN ENVIRONNEMENT JAVA (JRE/JDK) N'A ÉTÉ TROUVÉ !"
    echo "=================================================================="
    echo " Décompressez une JRE 17/21 dans le dossier : $APP_DIR/jre"
    echo "=================================================================="
    exit 1
fi

echo "[INFO] Version Java détectée :"
"$JAVA_CMD" -version || true
echo "------------------------------------------------------------------"

if [ ! -f "$APP_JAR" ]; then
    echo "[ERREUR] Impossible de trouver le fichier JAR : $APP_JAR"
    echo "Veuillez compiler l'application : cd back/server && mvn clean package -DskipTests"
    exit 1
fi

echo "[INFO] Démarrage du serveur Spring Boot..."
exec "$JAVA_CMD" -Xms64m -Xmx512m -jar "$APP_JAR" "$@"
