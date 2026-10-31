# 27 — Stratégie d'isolation multi-dépôts, BOM centralisé et gestion SemVer

**Statut** : Document de référence pour les agents et développeurs  
**Date d'établissement** : 31 octobre 2026  
**Objectif macroscopique** : Séparer les silos (`*-api` et `*-core`) dans des dépôts Git isolés tout en conservant une application monolithique exécutable et en garantissant une transition sans friction vers de futurs micro-services.

---

## 1. Vision et principes directeurs

### 1.1 Maintien du monolithe applicatif
Bien que le code soit découpé en plusieurs dépôts Git autonomes, l'application reste **déployée comme un monolithe exécutable unique** (JAR Spring Boot généré par `bootstrap`).  
- Pas de micro-services dans l'immédiat (pas de complexité réseau, pas de latence IPC, monitoring et déploiement simples).
- L'application consomme les modules de domaine sous forme de dépendances Maven (artefacts JAR publiés).

### 1.2 Préparation transparente aux micro-services (non-régression d'évolutivité)
Toutes les décisions prises doivent préserver la capacité future d'extraire n'importe quel silo en micro-service autonome sans réécrire le code métier :
- Les `*-api` restent purs (aucun framework, aucun Spring, aucun JPA, uniquement des contrats, modèles et ports Java).
- Les `*-core` ne communiquent jamais directement entre eux (visibilité tenue par Maven et ArchUnit).
- Si un silo doit devenir un micro-service demain, il suffira d'ajouter dans son dépôt un runner Spring Boot (exposant HTTP/REST ou gRPC) implémentant ses ports, sans modifier la logique métier.

---

## 2. Plan de mise en œuvre par étapes

```
+-------------------------------------------------------------------------------+
| Étape 1 : Correction des lanceurs et scripts de démarrage                     |
|           (Pointer MyFamilyBudget.bat, .sh et setup.js sur `bootstrap`)       |
+-------------------------------------------------------------------------------+
                                      │
                                      ▼
+-------------------------------------------------------------------------------+
| Étape 2 : Traitement et élimination du module `server`                        |
|           - server/src/main est vide                                          |
|           - Déplacement de server/src/test vers bootstrap/src/test            |
|           - Déplacement de server/doc vers back/doc                           |
|           - Retrait de `server` de back/pom.xml et mise à jour de la gate     |
+-------------------------------------------------------------------------------+
                                      │
                                      ▼
+-------------------------------------------------------------------------------+
| Étape 3 : Mise en place du BOM (`myfamilybudget-bom`)                         |
|           - Création du module BOM portant dependencyManagement               |
|           - Découplage de la dépendance <parent> réacteur                     |
|           - Capacité pour chaque dépôt de compiler de manière autonome        |
+-------------------------------------------------------------------------------+
                                      │
                                      ▼
+-------------------------------------------------------------------------------+
| Étape 4 : Gestion de versions SemVer, plages dynamiques et Release Notes      |
|           - Déclaration des dépendances en plages [1.0.0, 2.0.0)              |
|           - Obligation stricte de `release-note.md` à chaque publication      |
|           - Garde-fou CI de compatibilité binaire (revapi / japicmp)          |
+-------------------------------------------------------------------------------+
```

---

## 3. Détail des étapes

### Étape 1 — Correction des lanceurs et scripts de démarrage (✅ Fait !)

Depuis le patch **R-91**, le composition root exécutable Spring Boot est porté par le module `bootstrap` (`bootstrap-1.0.0-SNAPSHOT.jar`). `server` ne génère plus de jar exécutable.

**Actions réalisées** :
1. `MyFamilyBudget.bat` :
   - Chemin de fallback pointant vers `back\bootstrap\target\bootstrap-1.0.0-SNAPSHOT.jar`.
   - Commande d'aide à la compilation : `mvn clean package -DskipTests -pl bootstrap -am`.
2. `MyFamilyBudget.sh` :
   - Chemin pointant vers `back/bootstrap/target/bootstrap-1.0.0-SNAPSHOT.jar`.
   - Commande d'aide : `-pl bootstrap -am`.
3. `tests/e2e/setup.js` :
   - Lancement du backend pointant sur le module `bootstrap` au lieu de `server`.

---

### Étape 2 — Traitement et élimination du module `server` (✅ Fait !)

Le module `server` ne contenait plus aucun code de production (`src/main` était 100 % vide). Il n'embarquait que des tests d'intégration, les garde-fous ArchUnit et la documentation.

**Actions réalisées** :
1. Déplacement de `back/server/src/test` vers `back/bootstrap/src/test` :
   - `bootstrap` possède toutes les dépendances de production nécessaires sur le classpath (`web`, `application`, l'ensemble des silos `*-api` et `*-core`, `infra-jpa`).
   - Dépendance `archunit-junit5` ajoutée à `bootstrap/pom.xml`.
   - `locateBackRoot()` adapté dans `MavenModuleGraphTest` et `ModuleInfoVisibilityArchTest` pour cibler `bootstrap`.
   - Les tests d'architecture ArchUnit et les tests d'intégration (795 tests) s'exécutent avec succès dans `bootstrap`.
2. Déplacement du dossier `back/server/doc` vers `back/doc`.
3. Retrait du module `server` de `back/pom.xml`.
4. Adaptation des gardes-fous dans `tests/gate/check-gate.js` (ciblage de `bootstrap` pour Surefire et rapports Postgres) et `tests/gate/select-modules.js` (exclusion de `back/doc`).

---

### Étape 3 — Système de BOM (`myfamilybudget-bom`) (✅ Fait !)

Pour qu'un module de silo puisse être extrait dans son propre dépôt Git, il ne doit plus être contraint par une balise `<parent>` pointant vers un `../pom.xml` relatif inexistant dans un repo isolé.

#### Implémentation réalisée :
1. **Création du module autonome `myfamilybudget-bom`** :
   - Fichier [`back/myfamilybudget-bom/pom.xml`](file:///D:/Depots/MyFamilyBudget/back/myfamilybudget-bom/pom.xml) avec packaging `pom` sans parent requis.
   - Importe `spring-boot-dependencies:3.4.2` en `<scope>import</scope>`.
   - Centralise les dépendances tierces partagées (Jackson Databind Nullable, Swagger Annotations, ArchUnit, POI OOXML, Web Push, BouncyCastle, HttpClient).
   - Centralise l'ensemble des modules applicatifs et de silos internes (`com.moe.myfamilybudget:*`).
2. **Intégration au reactor et garde-fous** :
   - Module `myfamilybudget-bom` enregistré en tête de `<modules>` dans [`back/pom.xml`](file:///D:/Depots/MyFamilyBudget/back/pom.xml) et déclaré dans son `<dependencyManagement>`.
   - Ajout du POM du BOM dans `POMS` de [`tests/gate/check-gate.js`](file:///D:/Depots/MyFamilyBudget/tests/gate/check-gate.js).
3. **Usage dans un dépôt autonome** :
   Chaque projet de silo autonome importe désormais le BOM dans son propre `<dependencyManagement>` :
   ```xml
   <dependencyManagement>
       <dependencies>
           <dependency>
               <groupId>com.moe.myfamilybudget</groupId>
               <artifactId>myfamilybudget-bom</artifactId>
               <version>${myfamilybudget-bom.version}</version>
               <type>pom</type>
               <scope>import</scope>
           </dependency>
       </dependencies>
   </dependencyManagement>
   ```
4. **Validation** :
   - Validation autonome du BOM : `mvn -f back/myfamilybudget-bom/pom.xml validate` (SUCCESS).
   - Validation reactor complet : `mvn -f back/pom.xml test -pl bootstrap -am` (795 tests exécutés, 0 échec).
   - Garde-fou d'architecture et de qualité : `node tests/gate/check-gate.js all` (OK).

---

### Étape 4 — Gestion SemVer, plages de versions dynamiques et Release Notes (✅ Fait !)

#### 4.1 Politique SemVer stricte (Majeur.Mineur.Patch)
Chaque silo publie ses versions selon la spécification SemVer 2.0.0 :
- **PATCH (`x.y.Z`)** : Correction de bug interne dans `*-core`, optimisation de performance, documentation. **Zéro modification de signature ou de comportement dans `*-api`**.
- **MINEUR (`x.Y.0`)** : Ajout de nouvelle fonctionnalité, ajout d'une nouvelle méthode avec implémentation par défaut (`default method`) dans une interface de port `*-api`. **Rétro-compatibilité ascendante stricte (source et binaire)**.
- **MAJEUR (`X.0.0`)** : Rupture de contrat d'API (suppression ou renommage de port, modification de signature de méthode, changement de modèle DTO ou valeur de retour).

#### 4.2 Utilisation des plages de versions dynamiques dans les consommateurs
Dans l'application intégratrice (`bootstrap` / `application`), afin de ne pas devoir retoucher le `pom.xml` à chaque livraison mineure ou patch d'un silo :
```xml
<dependency>
    <groupId>com.moe.myfamilybudget</groupId>
    <artifactId>credit-api</artifactId>
    <version>[1.0.0, 2.0.0)</version>
</dependency>
<dependency>
    <groupId>com.moe.myfamilybudget</groupId>
    <artifactId>credit-core</artifactId>
    <version>[1.0.0, 2.0.0)</version>
</dependency>
```
- La syntaxe `[1.0.0, 2.0.0)` signifie : $\ge 1.0.0$ et $< 2.0.0$.
- Maven résout automatiquement la version mineure/patch la plus élevée disponible lors de la construction.
- Une évolution rétro-compatible est intégrée immédiatement sans modification de code.
- Une évolution cassante (`2.0.0`) est automatiquement ignorée tant qu'elle n'est pas explicitement acceptée.

#### 4.3 Contrainte impérative : `release-note.md` sur chaque publication
**Règle non négociable pour tout agent ou développeur** :  
Toute publication d'une nouvelle version d'un module/silo (qu'elle soit MAJEURE, MINEURE ou PATCH) **DOIT obligatoirement** s'accompagner de la mise à jour (ou création) d'un fichier `release-note.md` à la racine du dépôt concerné.

Le fichier initial [`release-note.md`](file:///D:/Depots/MyFamilyBudget/release-note.md) est initialisé à la racine du dépôt.

Chaque entrée de `release-note.md` doit contenir :
1. **En-tête** : Version exacte (`X.Y.Z`), date de publication, type de release (Majeur, Mineur, Patch).
2. **Résumé succinct** : Objectif de l'évolution en 1 ou 2 phrases.
3. **Changements par composant** :
   - `*-api` : Nouveaux ports, méthodes ajoutées, contrats modifiés ou inchangés.
   - `*-core` : Logique de calcul, algorithmes modifiés, corrections de bugs.
   - Persistance / Données : Impacts éventuels sur les tables SQL, changelogs Liquibase.
4. **Impact de rétro-compatibilité** : Mention explicite attestant du respect de la compatibilité binaire (ou décrivant la rupture si version majeure).

#### 4.4 Garde-fous automatisés et outillage de compatibilité binaire en CI
1. **Garde-fou `check-gate.js` (`checkSemVerAndReleaseNotes`)** :
   - Présence obligatoire de `release-note.md` avec en-têtes SemVer valides (`## [X.Y.Z]`) et sections obligatoires (Type de release, Résumé, Rétro-compatibilité).
   - Validation stricte des versions des POMs (`\d+\.\d+\.\d+(-SNAPSHOT)?`).
   - Contrôle du format des plages de versions dynamiques (`[X.0.0, (X+1).0.0)`).
2. **Outillage de compatibilité binaire (`japicmp-maven-plugin`)** :
   - `japicmp-maven-plugin` (version 0.23.1) configuré dans `<pluginManagement>` de [`back/pom.xml`](file:///D:/Depots/MyFamilyBudget/back/pom.xml).
   - Bloque le build (`<breakBuildOnBinaryIncompatibleModifications>true</breakBuildOnBinaryIncompatibleModifications>`) en cas de modification cassante de signature dans les APIs.

---

## 4. Consignes pour les agents futurs clonant ce dépôt

1. **Ne jamais réintroduire de code métier dans `server`** : le composition root de l'application est `back/bootstrap`.
2. **Ne jamais créer de dépendance directe entre deux modules `*-core`** : l'isolation des silos doit rester totale pour permettre leur extraction dans des dépôts distincts.
3. **Respecter scrupuleusement la règle du `release-note.md`** à chaque bump de version d'un composant.
4. **Toujours valider les modifications via** :
   - `mvn -f back/pom.xml test` (ou sur le sous-graphe concerné).
   - `node tests/gate/check-gate.js all`.
