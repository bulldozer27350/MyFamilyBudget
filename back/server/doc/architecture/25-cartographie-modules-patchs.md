# 25 — Cartographie modules → patch (clonage ciblé pour les agents)

Statut : 🟡 premier jet (établi le 20 octobre 2026, en appui du backlog `24-backlog-reste-a-faire.md`)

## 1. Pourquoi ce fichier

Le dépôt est déjà découpé en modules Maven, un dossier par silo (`back/<domaine>-api`,
`back/<domaine>-core`). Un agent qui clone l'intégralité du dépôt (12 Mo, ~860 fichiers suivis)
charge donc beaucoup de contexte inutile pour un patch qui ne touche qu'un ou deux domaines.
Ce fichier donne, pour chaque ligne encore ouverte de `24-backlog-reste-a-faire.md`, la liste
des dossiers à cloner (`git sparse-checkout`) — rien de plus.

Ce n'est **pas** un découpage en dépôts séparés (prématuré, voir `21-plan-silotage.md` et
`20-backlog-modules-maven-patchs.md`, MAVEN-160). C'est un allègement du clonage, réversible,
sans impact sur le code ni sur l'historique Git.

## 2. Recette de clonage

```bash
git clone --filter=blob:none --no-checkout <url> repo
cd repo
git sparse-checkout init --cone
git sparse-checkout set back/pom.xml <dossiers listés ci-dessous>
git checkout main
```

- `back/pom.xml` (descripteur du reactor Maven) est à inclure systématiquement : c'est le seul
  fichier à la racine de `back/`, il n'entraîne aucun code.
- Le mode cône (`--cone`) accepte des dossiers profonds (ex. un sous-package Java précis) aussi
  bien que des modules entiers : lister le chemin le plus profond qui couvre le besoin réel.
- `back/server` et `back/persistence` sont gros (2,6 Mo et 232 Ko) mais pas monolithiques pour
  le clonage : `back/server/doc/architecture` (docs) peut être inclus seul, sans
  `back/server/src` (code), quand le patch n'a besoin que de lire/modifier le backlog.
- Pour mettre à jour le statut d'une ligne dans `24-backlog-reste-a-faire.md` sans toucher au
  code, `back/server/doc/architecture` seul suffit.
- Cette cartographie est un premier jet : à affiner patch par patch si le dossier listé s'avère
  incomplet ou trop large à l'usage.

## 3. Vérification

| ID | Patch (résumé) | Dossiers à cloner | Note |
|---|---|---|---|
| V-01 | Confirmer en CI les tests de concurrence/redémarrage Objectifs et Banque | `back/goals-core`, `back/bank-pointage-core`, `.github/workflows` | Vérification de CI existante, pas de nouveau code |

## 4. Transverse

| ID | Patch (résumé) | Dossiers à cloner | Note |
|---|---|---|---|
| R-01 | Liquibase lot B : baseline H2+PostgreSQL, bascule `ddl-auto`, reprise de `LegacySchemaCleanup` | `back/server`, `back/infra-jpa`, et le dossier `src/main/resources/db/changelog/<module>` de **chaque** `*-core` (baseline par silo) | Le seul patch qui a vraiment besoin d'une vue large : il touche la config de tous les silos. Nécessite en plus une base H2 et une PostgreSQL vivantes pour générer/valider la baseline — hors de portée d'un agent sans accès DB |
| R-02 | Sortir les stores de paramètres Crédit et Objectifs de `server`/`application` | `back/credit-api`, `back/credit-core`, `back/goals-api`, `back/goals-core`, `back/server/src/main/java/com/moe/myfamilybudget/server/internal/calculation` (classes actuelles), `back/application-api` (port `ObjectifsSettingsStore`) | Chemin précis du code source vérifié dans cette session |

## 5. Persistance : lots B restants

| ID | Patch (résumé) | Dossiers à cloner | Note |
|---|---|---|---|
| R-51 | Retirer `SettingsEntity`/`SettingsPersistenceAdapter`/`SettingsRepository`/`syncSettings` ; script Liquibase PASS→`pension_plan` | `back/persistence`, `back/retirement-core` (cible de la copie), `back/server` | Dépend de R-01 livré au préalable ; contient un script Liquibase, mêmes réserves de validation que R-01 |
| R-61 | Supprimer le hub : `BudgetDataEntity`, `PersistenceManager`, `BudgetMutationService`, `BudgetPersistenceGateway`, `EntityModelConverter`, `DomainMutations`, module `transition-snapshot`, fixtures | `back/persistence`, `back/transition-snapshot`, `back/server`, `back/application` (fixtures de test), `back/pom.xml` (retrait du module) | Le plus large après R-01 : supprime un module entier et des classes référencées par plusieurs silos. Vérifier aussi les éventuels `requires`/dépendances Maven vers `transition-snapshot` dans les `*-api` avant de clôturer |

## 6. Porte B

| ID | Patch (résumé) | Dossiers à cloner | Note |
|---|---|---|---|
| R-70 | Vérifications PostgreSQL/redémarrage/E2E, test du préfixe de table par silo, procédure de déploiement | `back/server`, `back/infra-jpa`, `tests/e2e`, `tests/gate`, `.github/workflows`, `docker-compose.prod.yml` | Surtout des tests et de la CI, peu de code métier nouveau |
| R-71 | Constat de porte franchie (documentaire) | `back/server/doc/architecture` (uniquement, pas `back/server/src`), `tests/gate` | Patch de constat, pas de code |

## 7. Application et web

| ID | Patch (résumé) | Dossiers à cloner | Note |
|---|---|---|---|
| R-80 | Cas d'usage Fiscalité | `back/application-api`, `back/tax-api`, `back/application` | |
| R-81 | Cas d'usage Patrimoine | `back/application-api`, `back/wealth-api`, `back/application` | |
| R-82 | Cas d'usage Trésorerie | `back/application-api`, `back/treasury-api`, `back/application` | |
| R-83 | Cas d'usage Banque et Pointage | `back/application-api`, `back/bank-pointage-api`, `back/application` | |
| R-84 | Cas d'usage Analyse | `back/application-api`, `back/analysis-api`, `back/application` | |
| R-85 | Cas d'usage Overview | `back/application-api`, `back/application`, `back/retirement-api`, `back/tax-api`, `back/treasury-api`, `back/wealth-api` | Overview agrège les quatre domaines : vue plus large que les autres cas d'usage |
| R-86 | Cas d'usage Prêts et taux de marché | `back/application-api`, `back/credit-api`, `back/market-api`, `back/wealth-api`, `back/server` (code actuel : `AnalysePretsServiceImpl`, `PlacementRateSuggestion*`) | Propriétaire tranché le 14/10 : cas d'usage transverse Patrimoine+Marché |
| R-87 | Cas d'usage Notifications | `back/application-api`, `back/notifications-api`, `back/notifications-core`, `back/server` (câblage `NotificationBudgetMutationListener`) | |
| R-88 | Cas d'usage Paramètres et Système | `back/application-api`, `back/settings-api`, `back/server` | Dépend de R-52 (déjà fait) |
| R-90 | Module `web` : contrôleurs, mappers DTO, `GlobalExceptionHandler`, CORS, `HeartbeatController` | `back/server` (emplacement actuel des contrôleurs), `back/api` (interfaces OpenAPI générées), `back/application-api`, `openapi/`, `back/pom.xml` (nouveau module) | |
| R-91 | Module `bootstrap` : démarrage, câblage, planificateurs, `TransactionRunner`, `SiloMutationLockRegistry`, `DesktopLauncher` | `back/server` (emplacement actuel), `back/infra-jpa`, `back/pom.xml` | |
| R-92 | `module-info` sur `application-*`/`web`, règles de visibilité complètes | `back/application-api`, `back/application`, le nouveau module `web` (issu de R-90), et les `*-api` de chaque domaine (pour les `requires`) | Ne touche pas aux `*-core` (D3, pas de `module-info` dessus) |
| R-93 | CI sélective par sous-graphe | `.github/workflows`, `tests/gate`, `back/pom.xml` | Surtout de la config CI, peu de code Java |
| R-94 | Porte C : état cible vérifié (E2E, Docker, matrice de dépendances) | `tests/e2e`, `tests/gate`, `docker-compose.yml`, `docker-compose.prod.yml`, `back/server/doc/architecture` | Patch de vérification, pas de code nouveau |
