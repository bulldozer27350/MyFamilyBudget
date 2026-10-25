# 25 — Cartographie modules → patch (clonage ciblé pour les agents)

Statut : 🟡 v2 (20 octobre 2026, enrichi le 25 octobre 2026 en appui du backlog
`24-backlog-reste-a-faire.md`)

## 1. Pourquoi ce fichier

Le dépôt est déjà découpé en modules Maven, un dossier par silo (`back/<domaine>-api`,
`back/<domaine>-core`). Un agent qui clone l'intégralité du dépôt (12 Mo, ~860 fichiers suivis)
charge donc beaucoup de contexte inutile pour un patch qui ne touche qu'un ou deux domaines.

Ce fichier est la **fiche de démarrage** d'un patch : pour chaque ligne encore ouverte de
`24-backlog-reste-a-faire.md`, il donne en un seul endroit ses dépendances, les dossiers à
cloner et les décisions d'architecture à respecter. Un agent qui démarre un patch n'a besoin
que de deux lectures : la ligne du patch dans `24-backlog-reste-a-faire.md` (texte complet et
**statut à jour** — jamais dupliqué ici, il change trop souvent) et la ligne correspondante
ci-dessous.

Ce n'est **pas** un découpage en dépôts séparés (prématuré, voir `21-plan-silotage.md` et
`20-backlog-modules-maven-patchs.md`, MAVEN-160). C'est un allègement du clonage et de la
recherche de contexte, réversible, sans impact sur le code ni sur l'historique Git.

**Limite assumée** : ce fichier couvre le lot « persistance / Porte B » (V-01 à R-71) et une
première estimation du lot « Application et web » (R-80 à R-94). Les colonnes Dépendances et
Dossiers restent valables pour ce second lot, mais la colonne Décisions y est volontairement
vide : les DA de ce lot n'existent pas encore (nature différente des problèmes : câblage
applicatif, `module-info`, CI sélective — pas de schéma, pas de Liquibase). À enrichir au fil
de l'eau quand ces décisions seront prises, plutôt qu'anticipées ici.

## 2. Recette de clonage

```bash
git clone --filter=blob:none --no-checkout <url> repo
cd repo
git sparse-checkout init --cone
git sparse-checkout set back/pom.xml <dossiers listés ci-dessous>
git checkout main
```

- `back/pom.xml` (descripteur du reactor Maven) est à inclure systématiquement.
- Le mode cône (`--cone`) accepte des dossiers profonds (ex. un sous-package Java précis) aussi
  bien que des modules entiers : lister le chemin le plus profond qui couvre le besoin réel.
- `back/server` et `back/persistence` sont gros mais pas monolithiques pour le clonage :
  `back/server/doc/architecture` (docs) peut être inclus seul, sans `back/server/src` (code).
- Si le périmètre listé s'avère incomplet ou trop large à l'usage, étends le sparse-checkout
  plutôt que de repartir sur un clone complet, et signale l'écart (ce fichier sera corrigé).

## 3. Vérification

| ID | Dépend de | Dossiers à cloner | Décisions à consulter |
|---|---|---|---|
| V-01 | — | `back/goals-core`, `back/bank-pointage-core`, `.github/workflows` | — |

## 4. Transverse

| ID | Dépend de | Dossiers à cloner | Décisions à consulter |
|---|---|---|---|
| R-02 | — | `back/credit-api`, `back/credit-core`, `back/goals-api`, `back/goals-core`, `back/server/src/main/java/com/moe/myfamilybudget/server/internal/calculation`, `back/application-api` | — |

## 5. Porte B

| ID | Dépend de | Dossiers à cloner | Décisions à consulter |
|---|---|---|---|
| R-61 | R-01, R-51, R-52, R-60 | Découpé en 27 sous-patchs (`R-61-01` à `R-61-27`), voir `26-sequencage-r61-suppression-hub.md` — chaque sous-patch y liste ses propres fichiers, plus fins que `back/persistence`/`back/transition-snapshot`/`back/server`/`back/application` au complet | DA-02 (aucune FK inter-silo : rien à préserver côté hub), DA-03 (script Liquibase de suppression, pas de `ddl-auto`), DA-04 (`LegacyObjectifAllocationMigrator` supprimé, pas déplacé), DA-07 (dernier maillon de la sortie du cache global, séquencement respecté), DA-12 (bornes de `SettingsModel`/`SettingsReader`/`BudgetDataModelAllowList`, voir `26`) |
| R-70 | R-61 | `back/server`, `back/infra-jpa`, `tests/e2e`, `tests/gate`, `.github/workflows`, `docker-compose.prod.yml` | DA-02 (le test de préfixe de table à écrire est exactement celui que DA-02 note comme absent aujourd'hui) |
| R-71 | R-11, R-22, R-31, R-41, R-43, R-70 | `back/server/doc/architecture` (uniquement, pas `back/server/src`), `tests/gate` | Constat de porte, pas de nouvelle décision : relit DA-01 à DA-07 pour vérifier qu'aucune n'est restée "non appliquée" |

## 6. Application et web (dépendances et dossiers posés ; décisions à compléter plus tard)

| ID | Dépend de | Dossiers à cloner | Décisions à consulter |
|---|---|---|---|
| R-80 | — | `back/application-api`, `back/tax-api`, `back/application` | — |
| R-81 | — | `back/application-api`, `back/wealth-api`, `back/application` | — |
| R-82 | — | `back/application-api`, `back/treasury-api`, `back/application` | — |
| R-83 | — | `back/application-api`, `back/bank-pointage-api`, `back/application` | — |
| R-84 | — | `back/application-api`, `back/analysis-api`, `back/application` | — |
| R-85 | — | `back/application-api`, `back/application`, `back/retirement-api`, `back/tax-api`, `back/treasury-api`, `back/wealth-api` | — |
| R-86 | — | `back/application-api`, `back/credit-api`, `back/market-api`, `back/wealth-api`, `back/server` (code actuel : `AnalysePretsServiceImpl`, `PlacementRateSuggestion*`) | — |
| R-87 | — | `back/application-api`, `back/notifications-api`, `back/notifications-core`, `back/server` | — |
| R-88 | R-52 | `back/application-api`, `back/settings-api`, `back/server` | — |
| R-90 | R-80 à R-88 | `back/server` (contrôleurs actuels), `back/api`, `back/application-api`, `openapi/`, `back/pom.xml` | — |
| R-91 | R-90, R-02, R-71 | `back/server`, `back/infra-jpa`, `back/pom.xml` | — |
| R-92 | R-91 | `back/application-api`, `back/application`, le module `web` (issu de R-90), les `*-api` de chaque domaine | DA-01 (`module-info` sur `*-api`/`application-*`/`web` uniquement, aucun sur les `*-core`) |
| R-93 | R-92 | `.github/workflows`, `tests/gate`, `back/pom.xml` | — |
| R-94 | R-93 | `tests/e2e`, `tests/gate`, `docker-compose.yml`, `docker-compose.prod.yml`, `back/server/doc/architecture` | — |
