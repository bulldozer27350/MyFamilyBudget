@echo off
REM ==============================================================================
REM  MyFamilyBudget - Lanceur Portable Autonome (Windows)
REM  1. Ouvre instantanement le SplashScreen web local (0 seconde)
REM  2. Lance le serveur Spring Boot avec la JRE embarquee ou systeme
REM ==============================================================================

setlocal enabledelayedexpansion
title MyFamilyBudget - Livre de comptes

set "APP_DIR=%~dp0"
set "APP_JAR=%APP_DIR%MyFamilyBudget.jar"
set "SPLASH_HTML=%APP_DIR%splashscreen.html"

REM Fallback si execute depuis la racine du code source Maven
if not exist "%APP_JAR%" set "APP_JAR=%APP_DIR%back\server\target\server-1.0.0-SNAPSHOT.jar"

echo ==================================================================
echo             Lancement de MyFamilyBudget en cours...
echo ==================================================================
echo [INFO] Repertoire de l'application : "%APP_DIR%"

REM 0. Ouverture instantanee du SplashScreen dans le navigateur par defaut
if exist "%SPLASH_HTML%" (
    echo [INFO] Ouverture immediate du SplashScreen...
    start "" "%SPLASH_HTML%"
)

REM 1. Verification de la JRE embarquee dans /jre
if exist "%APP_DIR%jre\bin\java.exe" (
    set "JAVA_CMD=%APP_DIR%jre\bin\java.exe"
    echo [INFO] Utilisation de la JRE embarquee dans /jre
    goto :check_jar
)

REM 2. Verification de la JRE embarquee dans /runtime
if exist "%APP_DIR%runtime\bin\java.exe" (
    set "JAVA_CMD=%APP_DIR%runtime\bin\java.exe"
    echo [INFO] Utilisation de la JRE embarquee dans /runtime
    goto :check_jar
)

REM 3. Fallback sur JAVA_HOME
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
        echo [INFO] Utilisation de JAVA_HOME : %JAVA_HOME%
        goto :check_jar
    )
)

REM 4. Fallback sur la commande 'java' du PATH
where java >nul 2>nul
if %ERRORLEVEL% equ 0 (
    set "JAVA_CMD=java"
    echo [INFO] Utilisation du Java present dans le PATH systeme
    goto :check_jar
)

REM Si aucun Java n'est disponible
echo.
echo ==================================================================
echo  [ERREUR] AUCUN ENVIRONNEMENT JAVA N'A ETE TROUVE !
echo ==================================================================
echo  Pour rendre cette application 100%% autonome sans installation :
echo   1. Telechargez une JRE 17 ou 21 - ex: Eclipse Temurin JRE 21 ZIP
echo   2. Decompressez-la dans le sous-dossier "jre" de cette application :
echo      "%APP_DIR%jre\bin\java.exe"
echo.
echo  Ou installez Java 17/21+ sur votre systeme et declarez-le dans le PATH.
echo ==================================================================
echo.
pause
exit /b 1

:check_jar
echo [INFO] Commande Java detectee : "%JAVA_CMD%"
echo [INFO] Version Java :
"%JAVA_CMD%" -version
echo ------------------------------------------------------------------

if exist "%APP_JAR%" goto :launch_app

echo.
echo ==================================================================
echo [ERREUR] Impossible de trouver le fichier JAR de l'application :
echo "%APP_JAR%"
echo.
echo Si vous travaillez sur le code source, veuillez d'abord compiler avec :
echo   cd back\server ^&^& mvn clean package -DskipTests
echo ==================================================================
echo.
pause
exit /b 1

:launch_app
echo [INFO] Fichier JAR cible : "%APP_JAR%"
echo [INFO] Demarrage du serveur Spring Boot...
echo ==================================================================
echo.

REM Execution du JAR autonome (Spring Boot)
"%JAVA_CMD%" -Xms64m -Xmx512m -jar "%APP_JAR%" %*

set "EXIT_CODE=%ERRORLEVEL%"
echo.
echo ==================================================================
echo  Le serveur MyFamilyBudget s'est arrete - Code de sortie : %EXIT_CODE%
echo ==================================================================
if %EXIT_CODE% neq 0 (
    echo [ERREUR] Une anomalie s'est produite au demarrage ou a l'execution.
)
echo Appuyez sur une touche pour fermer cette fenetre...
pause >nul
