# 21 — Plan de travail : silotage complet des données, `application` ≠ web

Statut : 🟢 décisions D1 à D8 tranchées (document établi le 4 octobre 2026 à partir de `main` @ `6c53693` et d'une relecture des documents `00` à `20`)

## 1. Objectif visé (reformulation)

1. **Silos de services isolés** : chaque domaine (Retraite, Fiscalité, Patrimoine, Trésorerie, Banque/Pointage, Analyse,
   Crédit, Objectifs, Notifications, Marché) est un silo qui ne connaît aucun autre silo.
2. **Persistance dans chaque silo** : entités, repositories et mappers JPA appartiennent au silo qui possède la donnée. Seul un
   module d'infrastructure commun porte la configuration technique (Hibernate, source de données, nommage).
3. **`application` seule transverse** : elle orchestre les silos pour fournir une réponse cohérente. Elle ne connaît que leurs
   **API** (ports, contrats, interfaces de service), jamais leurs entités ni leurs implémentations.
4. **`application` ≠ serveur web** : le serveur web expose les services, convertit les entrées/sorties vers le monde web
   (DTO OpenAPI, `ResponseEntity`, erreurs HTTP) et délègue chaque demande à l'application.
5. **`BudgetDataModel` supprimé.** Seule entorse admise : un usage de transition strictement borné par un patch de
   suppression **déjà planifié** et ordonné juste après.

## 2. Écarts entre l'existant documenté et cet objectif

Les documents `13`, `20` et le backlog `18` ont été rédigés avec d'autres hypothèses. Elles doivent être révisées (SILO-001) :

| Document | Hypothèse actuelle | Conflit avec l'objectif |
|---|---|---|
| `13-persistance.md` O3 / R3 | `BudgetDataModel` survit comme snapshot global (import/export/backup) | doit disparaître : l'application compose l'export à partir de fragments de silo |
| `13-persistance.md` C1 | une seule base PostgreSQL | compatible (une base, des tables par silo), à expliciter : c'est ce qui rend la transaction possible |
| `18` DB-1180 | « réduire `BudgetDataModel` au snapshot global » | ne le supprime pas : remplacé par SILO-230 |
| `20` principes + MAVEN-120 | un seul module `persistence` | le module monolithique doit être éclaté par silo |
| `20` MAVEN-102 | `application` implémente les interfaces `*Api` générées (`ResponseEntity`) | `application` est collée au web |
| `00-principes.md` | contrats partagés entre domaines (projections publiées) | à trancher (décision D1) |

## 3. État mesuré le 4 octobre 2026

| Sujet | Constat |
|---|---|
| Modules Maven | 10 modules de domaine purs (avec `module-info`), `transition-snapshot`, `api`, `application`, `persistence`, `server` |
| Domaines purs | aucun usage réel de `BudgetDataModel` (seulement des commentaires) |
| `BudgetDataModel` dans `application` | 21 fichiers (7 services dont six avec `composeBudgetData()`, 9 factories, 4 mappers, snapshot global) |
| `BudgetDataModel` dans `server` | 2 fichiers (`AnalysePretsServiceImpl`, `PlacementRateSuggestionInputFactory`) |
| `BudgetDataModel` dans `persistence` | 7 fichiers dont `BudgetMutationService` (1093 lignes), `BudgetCacheStore`, `BudgetPersistenceGateway`, `PersistenceManager`, `EntityModelConverter` |
| Hub JPA `BudgetDataEntity` | relations retirées : Retraite, Fiscalité, Objectifs, Banque (DB-1100 à DB-1130). Restent : Settings, Incomes, Charges, Placements, RealEstate, OneOff, Transfers, Variable*, AssetCategories, Loans |
| Lecture | sept readers lisent des tables autonomes ; `SettingsReader` lit encore le cache global |
| `application` | 61 classes ; 12 touchent Spring Web ; elle implémente les `*Api` générés ; elle ne dépend pas de `persistence` (bon point) |
| `server` | 30 composants Spring restants : marketdata, enablebanking, notifications (dispatch, canaux), services de taux, quatre `Jpa*Store` |
| Moteurs | `application` instancie directement les moteurs (`new TresorerieCalculationService()`…) : elle connaît des implémentations |
| Transactions | `@Transactional` dans 2 classes d'`application`, 4 de `persistence`, 1 de `server` ; un seul `PlatformTransactionManager` |

## 4. Réponses aux trois questions

### 4.1 Que reste-t-il pour isoler les services dans des silos ?

1. **Supprimer `BudgetDataModel` et `SettingsModel` des services et factories** (SILO-100 à SILO-120) : réécrire chaque
   service sur des fragments lus via les ports du silo propriétaire.
2. **Casser les contrats croisés entre silos** (SILO-130 à SILO-134, selon D1) : Trésorerie consomme aujourd'hui des types
   de Retraite, Fiscalité et Patrimoine ; Analyse, Patrimoine et Trésorerie partagent `domain-budget`.
3. **Séparer API et cœur de chaque silo** (SILO-140 à SILO-160) : l'application ne manipule plus que des interfaces ; les
   moteurs sont câblés ailleurs.
4. **Terminer les reliquats** : Marché/Enable Banking et Notifications (SILO-170, SILO-180).
5. **Remplacer l'import/export/reset global** par une composition applicative de fragments (SILO-119).

### 4.2 Que reste-t-il pour isoler la persistance, et est-ce compatible avec la transaction ?

**Oui, c'est compatible**, à trois conditions, toutes réalisables ici :

- **une seule base et un seul `PlatformTransactionManager`** (déjà le cas) ; les silos partagent la même `EntityManagerFactory`,
  chacun avec son propre package d'entités, de repositories et de mappers ;
- **aucune relation JPA entre silos** (principe C2 du document 13, déjà tenu pour quatre domaines) : les liens inter-silos sont
  des identifiants ; l'intégrité devient une règle applicative exécutée dans la même transaction (SILO-240) ;
- **la transaction est ouverte par l'application via un port** (`TransactionRunner`, SILO-205) dont l'implémentation Spring
  (`TransactionTemplate`) est fournie par le module de démarrage. L'application n'a plus besoin d'`@Transactional` ni de
  Spring, et la même transaction couvre plusieurs silos.

Ce qui reste : éclater `persistence` par silo (SILO-210 à SILO-218), dissoudre `SettingsEntity` chez ses propriétaires
(SILO-220), supprimer le cache global et les mutations génériques (SILO-206, SILO-230), vérifier PostgreSQL et le
redémarrage (SILO-250). Limite assumée : si un jour deux silos devaient avoir des bases physiques distinctes, l'atomicité
multi-silos ne serait plus gratuite (saga ou cohérence éventuelle). Ce plan ne l'anticipe pas.

### 4.3 Que reste-t-il pour séparer `application` et le web ?

Créer une API applicative (`application-api` : cas d'usage et modèles applicatifs, sans DTO OpenAPI ni `ResponseEntity`),
déplacer les contrôleurs, les mappers DTO et la gestion d'erreurs HTTP dans un module `web`, puis ne garder dans un module
`bootstrap` que le câblage Spring (SILO-300 à SILO-340). Les 12 fichiers d'`application` qui touchent Spring Web et les
12 mappers sont le gros du volume ; peu de risque métier.

## 5. Architecture cible

```text
bootstrap        (Spring Boot, câblage, DesktopLauncher ; seul à voir les implémentations)
   │ runtime
   ├── web                 contrôleurs implémentant les *Api générés, DTO ↔ modèles applicatifs, erreurs HTTP
   │      └── requires application-api, api (OpenAPI généré)
   ├── application-core    orchestration, composition, règles d'intégrité inter-silos
   │      └── requires application-api, *-api des silos, tx (port TransactionRunner)
   ├── <silo>-core × N     moteur + persistance JPA du silo (entités, repositories, mappers)
   │      └── requires <silo>-api, infra-jpa
   └── infra-jpa           configuration Hibernate, source de données, nommage ; aucun type métier

application-api   cas d'usage + modèles applicatifs
<silo>-api × N    ports (Reader/Writer), contrats d'entrée/sortie, interfaces de service, module-info
```

Règles vérifiées à la compilation (Maven enforcer et module-info) puis par ArchUnit :

- `application-*` et `web` ne dépendent d'aucun `*-core` ni de `infra-jpa` ;
- un `*-api` ne dépend d'aucun autre silo (selon D1) ;
- aucun `*-core` ne dépend d'un autre `*-core` ;
- `BudgetDataModel` et `transition-snapshot` n'existent plus.

## 6. Décisions à trancher (patch SILO-000)

| # | Question | Recommandation | Décision |
|---|---|---|---|
| D1 | Un silo peut-il consommer des types publiés par un autre silo (ex. Trésorerie utilise `TaxProjection`) ? | **Non** : chaque consommateur définit ses propres entrées, l'application traduit. Plus de code de mapping, mais indépendance réelle. Si refusé, le plan reste valable avec les contrats `*-api` partagés (SILO-130 à SILO-134 deviennent facultatifs) | **D'accord avec la recommandation** |
| D2 | Sort de `domain-budget` (Income, Charge, Transfer, TripleAmount…) | Les revenus/charges appartiennent à Trésorerie (document 13). Les autres silos reçoivent leurs propres types. Un petit module de **valeurs pures sans sens métier** (ex. triple pessimiste/réaliste/optimiste) est toléré s'il n'est pas un « common » fourre-tout | **D'accord avec la recommandation** |
| D3 | Périmètre de `module-info` | Sur tous les `*-api`, `application-*`, `web`. Pas sur les `*-core` JPA/Spring (modules automatiques, `opens` coûteux) : la visibilité y est tenue par le graphe Maven et ArchUnit. Le runtime Spring Boot reste sur le classpath, donc `module-info` y est ignoré | **D'accord avec la recommandation** |
| D4 | Schéma de base | Une base, tables préfixées par silo (déjà le cas : `cashflow_`, `wealth_`, `fiscal_`, `pension_`, `goal_`, `credit_`). Pas de clé étrangère entre silos | **D'accord avec la recommandation** |
| D5 | Migrations de schéma | Aujourd'hui `ddl-auto: update` + procédure manuelle (suppression du schéma, réimport JSON). Choisir : maintenir cette procédure (mono-utilisateur) ou introduire Flyway par silo | **Introduire Liquibase comme système de migration** |
| D6 | Import/export/reset global | Chaque silo expose `export` / `replace` / `reset` sur ses propres records ; l'application compose le fichier JSON (format `BudgetDataDto` conservé côté web) et exécute l'import dans une transaction | **D'accord avec la recommandation** |
| D7 | Propriété de `marketdata` et `enablebanking` | `marketdata` : silo « Marché » (consommé par Crédit, Suggestions de taux, Patrimoine). `enablebanking` : intégration du silo Banque | **D'accord avec la recommandation** |
| D8 | `LegacyObjectifAllocationMigrator` | Supprimer (aucun objectif réel en base au moment de son introduction), sinon le déplacer dans le silo Objectifs | **D'accord avec la recommandation** |

### Décision D3 : périmètre de `module-info`

Un `module-info.java` déclare ce qu'un module Maven expose (`exports`) et ce dont il dépend (`requires`). Le compilateur refuse
alors tout import d'un package non exporté, même si la classe est `public`. C'est la garantie « `application` ne voit que les API »,
vérifiée dès la compilation.

Le coût apparaît sur les modules `*-core` (JPA, Spring) : Hibernate et Spring accèdent aux classes par réflexion, ce qui impose des
`opens` (ou un `open module`) et des modules automatiques pour les bibliothèques sans descripteur. Et dans le jar exécutable Spring
Boot, tout est sur le classpath : les `module-info` y sont ignorés à l'exécution. Le bénéfice se limite donc à la compilation et aux tests.

| Option | Contenu | Conséquence |
|---|---|---|
| A (recommandée) | `module-info` sur les `*-api`, `application-*` et `web` ; aucun sur les `*-core` ; visibilité des `*-core` tenue par le graphe Maven (enforcer, SILO-003) et ArchUnit | garantie forte là où elle compte, pas de `opens` à maintenir |
| B | `module-info` partout, `open module` sur les `*-core` | garantie uniforme, mais `opens` à maintenir et risques d'incompatibilité avec Hibernate/Spring |
| C | aucun `module-info` ; tout par Maven et ArchUnit | plus simple, mais les modules actuels des domaines purs perdent leur descripteur |

Réponse donnée par l'utilisateur : **A**. `module-info` sur les `*-api`, `application-*` et `web` ; aucun sur les `*-core`.

## 7. Vue d'ensemble des patchs

Statuts : tous « Non commencé » sauf mention « Acquis partiel ». Les identifiants existants (`DB-xxx`, `MAVEN-xxx`) restent valables
pour l'historique ; la dernière colonne indique ce qui est absorbé.

### Phase 0 : décisions et garde-fous

| ID | Titre | Prérequis | Absorbe / remplace |
|---|---|---|---|
| SILO-000 | Trancher les décisions D1 à D8 | aucun (validation de Marco) | — |
| SILO-001 | Réviser les principes (`00`, `13`, `20`) et marquer les patchs remplacés | SILO-000 | DB-1180, DB-1190, MAVEN-120 (périmètre), MAVEN-160 |
| SILO-002 | Garde-fou : liste fermée et datée des usages de `BudgetDataModel` (ne peut que décroître) | aucun | dérogation ArchUnit du port `GlobalBudgetSnapshotWriter` |
| SILO-003 | Garde-fous de graphe Maven (enforcer) selon la section 5 | SILO-000 | MAVEN-140 (partie) |

### Phase A : services isolés

| ID | Titre | Prérequis | Parallélisable avec |
|---|---|---|---|
| SILO-100 | Éclater la lecture de `SettingsModel` en paramètres par propriétaire | SILO-000 | SILO-002 |
| SILO-110 | Retraite sans `BudgetDataModel` | SILO-100 | SILO-111 à SILO-116 |
| SILO-111 | Fiscalité sans `BudgetDataModel` | SILO-100 | SILO-110, SILO-112 à SILO-116 |
| SILO-112 | Patrimoine sans `BudgetDataModel` | SILO-100 | SILO-110, SILO-111, SILO-113 à SILO-116 |
| SILO-113 | Trésorerie sans `BudgetDataModel` | SILO-100 | SILO-110 à SILO-112, SILO-114 à SILO-116 |
| SILO-114 | Banque/Pointage sans `BudgetDataModel` | SILO-100 | autres SILO-11x |
| SILO-115 | Analyse sans `BudgetDataModel` | SILO-100, SILO-114 | autres SILO-11x |
| SILO-116 | Crédit, Prêts et Suggestions de taux sans `BudgetDataModel` | SILO-100, MAVEN-103 lot B2 | autres SILO-11x |
| SILO-117 | Overview sans `BudgetDataModel` | SILO-110 à SILO-113 | SILO-118 |
| SILO-118 | Objectifs et Notifications : entrées par fragments | SILO-100 | SILO-117 |
| SILO-119 | Import/export/reset par fragments de silo | SILO-110 à SILO-118 | — |
| SILO-120 | `BudgetDataModel` supprimé d'`application` et de `server` | SILO-110 à SILO-119 | — |
| SILO-130 à SILO-134 | Contrats propres au consommateur (Tax←Retirement, Wealth←Budget, Treasury←3 silos, Analysis←Bank/Budget, Notifications←Goals) | SILO-000 (D1), SILO-110 à SILO-118 du silo concerné | entre eux |
| SILO-140 | Dissoudre `domain-budget` selon D2 | SILO-130 à SILO-134 | — |
| SILO-150 | Séparer API et cœur : silo pilote Retraite | SILO-140 | — |
| SILO-151 à SILO-159 | Séparer API et cœur : autres silos (un patch par silo) | SILO-150 | entre eux |
| SILO-160 | `application` ne dépend plus que des `*-api` ; moteurs câblés hors application | SILO-150 à SILO-159 | — |
| SILO-170 | Silo Marché et intégration Enable Banking sortis de `server` (D7) | SILO-116, SILO-153 | SILO-180 |
| SILO-180 | Notifications complètes dans leur silo (dispatch, canaux, ports de dédup/abonnements) | SILO-118, SILO-158 | SILO-170 |
| SILO-190 | Porte A : services isolés | SILO-120, SILO-160, SILO-170, SILO-180 | — |

### Phase B : persistance par silo

| ID | Titre | Prérequis | Absorbe |
|---|---|---|---|
| SILO-200 | Module `infra-jpa` (configuration Hibernate, source de données, nommage) | SILO-190 | — |
| SILO-205 | Port `TransactionRunner` et retrait des `@Transactional` de l'application | SILO-160, SILO-200 | — |
| SILO-206 | Remplacer le verrou global (`BudgetMutationLock`, cache) par un verrouillage par silo | SILO-205 | VT-350b (tests réutilisés) |
| SILO-210 | Persistance Retraite dans son silo | SILO-200, SILO-151 | — |
| SILO-211 | Persistance Fiscalité dans son silo | SILO-200, SILO-152 | — |
| SILO-212 | Persistance Objectifs dans son silo | SILO-200, SILO-154 | — |
| SILO-213 | Persistance Banque dans son silo | SILO-200, SILO-155 | — |
| SILO-214 | Persistance Crédit dans son silo (retrait du hub Crédit) | SILO-200, SILO-156 | DB-1140 |
| SILO-215 | Persistance Patrimoine dans son silo (retrait du hub Patrimoine) | SILO-200, SILO-153 | DB-1150 |
| SILO-216 | Persistance Trésorerie dans son silo (retrait du hub Trésorerie) | SILO-200, SILO-157 | DB-1160 |
| SILO-217 | Persistance Notifications et Marché dans leurs silos | SILO-200, SILO-170, SILO-180 | tests `Jpa*Store` |
| SILO-220 | Paramètres persistés chez leurs propriétaires (dissoudre `SettingsEntity`) | SILO-100, SILO-210 à SILO-216 | DB-061 (stockage) |
| SILO-230 | Suppression de `BudgetDataEntity`, `BudgetCacheStore`, `PersistenceManager`, `BudgetMutationService`, `BudgetPersistenceGateway`, `EntityModelConverter`, `BudgetDataModel`, `transition-snapshot` | SILO-206, SILO-210 à SILO-220, SILO-120 | DB-1170, DB-1180, DB-1190 |
| SILO-240 | Intégrité inter-silos portée par l'application | SILO-205, SILO-210 à SILO-216 | — |
| SILO-250 | Vérifications PostgreSQL, redémarrage, parcours E2E sans repli | SILO-230, SILO-240 | DB-1080, VT-320 |
| SILO-290 | Porte B : persistance par silo | SILO-250 | DB-1200, GATE-010 |

### Phase C : application et web

| ID | Titre | Prérequis | Absorbe |
|---|---|---|---|
| SILO-300 | `application-api` : cas d'usage et modèles applicatifs (pilote Retraite) | SILO-190 pour le pilote Retraite ; SILO-290 pour le reste | — |
| SILO-310 à SILO-319 | Un patch par fonctionnalité : contrôleur web + mapper DTO dans `web`, cas d'usage dans `application` (Retraite, Fiscalité, Patrimoine, Trésorerie, Banque/Pointage, Analyse, Overview, Prêts/Taux, Notifications, Paramètres/Système) | SILO-300 | MAVEN-102/103 (reliquat) |
| SILO-320 | Module `web` : contrôleurs, gestion des erreurs HTTP, CORS | SILO-310 à SILO-319 | — |
| SILO-330 | Module `bootstrap` : démarrage, câblage, scan des implémentations | SILO-320, SILO-290 | MAVEN-130 |
| SILO-340 | `module-info` sur `application-*`, `web` ; règles de visibilité complètes | SILO-330 | MAVEN-140 |
| SILO-350 | CI sélective par sous-graphe | SILO-340 | MAVEN-150 |
| SILO-390 | Porte C : état cible vérifié | SILO-340 | RF-D00 |

### Graphe de dépendances (résumé)

```text
SILO-000 ─► SILO-001, SILO-003, SILO-100 ─► SILO-110..118 ─► SILO-119 ─► SILO-120 ──┐
SILO-000 (D1) ─► SILO-130..134 ─► SILO-140 ─► SILO-150 ─► SILO-151..159 ─► SILO-160 ─┤
SILO-116/118 ─► SILO-170, SILO-180 ─────────────────────────────────────────────────┤
                                                                         SILO-190 ◄──┘
SILO-190 ─► SILO-200 ─► SILO-205 ─► SILO-206, SILO-240
                     └► SILO-210..217 ─► SILO-220 ─► SILO-230 ─► SILO-250 ─► SILO-290
SILO-290 ─► SILO-300 ─► SILO-310..319 ─► SILO-320 ─► SILO-330 ─► SILO-340 ─► SILO-350, SILO-390
```

Chemin critique : SILO-000 → SILO-100 → SILO-11x → SILO-119/120 → SILO-190 → SILO-205 → SILO-21x → SILO-230 → SILO-290 → SILO-31x → SILO-330.

## 8. Description des patchs

Chaque patch reste petit, mergeable seul et sans changement de contrat REST. Convention héritée de `18-backlog-persistance-patchs.md` :
un patch qui rencontre un arbitrage non tranché devient un patch « bloqué » et ne continue pas.

### SILO-000 : Trancher les décisions D1 à D8
- **Objectif** : lever les huit décisions de la section 6.
- **Travaux** : document seul. Marco valide ou amende chaque décision ; le résultat est consigné dans cette section.
- **Sortie** : section 6 marquée « validée » avec la décision retenue pour chaque ligne.
- **Avancement** : D1 à D8 tranchées (D3 : option A).
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente de réponse / [ ] Annulé / [x] Terminé

### SILO-001 : Réviser les principes et marquer les patchs remplacés
- **Objectif** : aligner `00-principes.md`, `13-persistance.md` (O3, R3, C1) et `20-backlog-modules-maven-patchs.md` (principes, MAVEN-120, 130, 140, 160) sur cette cible ; marquer « Annulé, remplacé par SILO-xxx » chaque item non terminé qui est absorbé, selon la section 10 (RF-D00, DB-1080, DB-1170, DB-1180, DB-1190, DB-1200, GATE-010, MAVEN-130 à MAVEN-150), sans supprimer aucun fichier.
- **Livré** : `00`, `12`, `13` (O3, R3, C1, snapshot, C5) et `20` (principes, MAVEN-120, note de tête) révisés ; RF-D00, DB-1080, DB-1170, DB-1180, DB-1190, DB-1200, GATE-010, MAVEN-130, MAVEN-140, MAVEN-150 marqués « Annulé, remplacé par SILO-xxx » ; MAVEN-160 reporté après SILO-390 (prérequis DB-1180 retiré). Aucun fichier supprimé.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente de réponse / [ ] Annulé / [x] Terminé

### SILO-002 : Garde-fou de péremption pour `BudgetDataModel`
- **Objectif** : empêcher toute extension de l'usage. Liste fermée des classes autorisées (celle d'aujourd'hui), test qui échoue si elle grandit, et chaque patch SILO-1xx retire des lignes.
- **Travaux** : remplace l'autorisation par nom du port `GlobalBudgetSnapshotWriter` par une liste datée avec, pour chaque entrée, le patch qui la supprime.
- **Sortie** : liste figée ; aucune entrée sans patch de suppression associé.
- **Livré** : `BudgetDataModelAllowList` (38 classes, gel du 4 octobre 2026, patch de suppression nommé pour chaque entrée) et `BudgetDataModelUsageArchTest` (échec si une classe hors liste dépend du modèle, si la liste grandit, si une entrée est dupliquée, sans patch SILO-xxx ou périmée). Chaque patch SILO-1xx, SILO-119, SILO-21x et SILO-230 retire ses lignes et abaisse `FROZEN_SIZE`. Les règles par package de l'ancien garde-fou CLEAN-010 sont supprimées ; le garde-fou `GlobalSnapshotBoundaryArchTest` (CLEAN-020) est conservé jusqu'à SILO-119.
- **Statut** : [x] Terminé

### SILO-003 : Garde-fous de graphe Maven
- **Objectif** : faire échouer le build si `application-*`/`web` dépendent d'un `*-core` ou de `infra-jpa`, si un silo dépend d'un autre silo (D1), ou si `BudgetDataModel` réapparaît.
- **Travaux** : `maven-enforcer` (règles de dépendances interdites) et test de graphe ; règles activées au fur et à mesure (liste de dérogations décroissante).
- **Livré** : test de graphe seul (`MavenModuleGraphTest`, `MavenModuleGraphRules`, `MavenModuleGraphExceptions`), qui lit les `pom.xml` du reactor sans configuration de plugin. Règles : (A) un silo ne dépend d'aucun autre silo ; (B) un silo ne dépend ni de l'orchestration, ni de `persistence`, `server`, `transition-snapshot`, `api` (ni de `infra-jpa` hors `*-core`) ; (C) `application*` et `web` ne dépendent d'aucun `*-core`, ni de `infra-jpa`, `persistence`, `server` ; (D) graphe acyclique. Les neuf arêtes actuelles entre modules `domain-*` sont une liste fermée datée du 4 octobre 2026, chacune avec son patch de suppression (SILO-130 à SILO-134, SILO-140) ; la liste ne peut que décroître et une entrée périmée fait échouer le test. Les règles B et C sont sans effet tant que les modules cibles n'existent pas. La règle « `application` ne dépend que des `*-api` » (SILO-160) et l'interdiction de `transition-snapshot` (SILO-230) ne sont pas encore activées. `maven-enforcer` n'est pas ajouté : il sera utile avec les modules `*-core`, sans valeur ajoutée ici.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente de réponse / [ ] Annulé / [x] Terminé

### SILO-100 : Éclater la lecture de `SettingsModel`
- **Objectif** : supprimer `SettingsReader` et `SettingsModel` de `transition-snapshot`.
- **Travaux** : chaque silo expose ses paramètres via son propre reader (Retraite : âge, année de naissance, PASS ; Fiscalité : abattement, âge de sortie des enfants ; Trésorerie : date et mode pivot, solde de départ, balayage ; Simulation ; Hypothèses économiques). 22 fichiers d'`application` consomment `SettingsModel` aujourd'hui. L'écriture est déjà distribuée (`SettingsCommandRouter`, DB-061).
- **Acquis** : écriture par propriétaire, façade REST composite, atomicité multi-domaines.
- **Découpage** : lot A (ports de lecture par propriétaire, additif) puis lot B (migration des consommateurs et suppression de `SettingsReader`). `SettingsModel` reste dans `transition-snapshot` : `BudgetDataModel` le contient jusqu'à SILO-230 et son stockage (`SettingsEntity`) est dissous par SILO-220.
- **Livré (lot A)** : `RetirementSettingsReader` (`birthYear`, `retireAge`), `TaxSettingsReader` (`childExitAge`, `taxAbattement`), `TresorerieSettingsReader` (pivot, solde de départ, sweep, seuils de cash) dans leurs domaines ; `SimulationSettingsReader` et `EconomicAssumptionsReader` dans `transition-snapshot`, à côté de leurs writers (aucun silo pour ces deux notions). `SettingsPersistenceAdapter` implémente les cinq ports en projetant `getSettings()` : il reste le seul lecteur du cache. Test `SettingsPersistenceAdapterOwnerReadersTest`.
- **Livré (lot B)** : `SettingsPersistenceAdapter` n'implémente plus `SettingsReader` ; `SettingsModelAssembler` (`application.settings`, classe de transition) recompose le `SettingsModel` à partir des cinq ports propriétaires et devient l'unique implémentation de `SettingsReader`. Les dix consommateurs d'`application` gardent `SettingsReader` (inchangés) ; `NotificationDispatchService` lit directement `TresorerieSettingsReader`. Tests : helper `SettingsReaderTestFactory`, assembleur comparé à `getSettings()`.
- **Reste** : `SettingsReader` et `SettingsModelAssembler` ne disparaissent qu'avec leurs derniers consommateurs (SILO-110 à SILO-118 : chaque service lit alors les ports propriétaires), au plus tard SILO-120. `SettingsModel` reste jusqu'à SILO-230.
- **Statut** : [ ] Non commencé / [x] Démarré / [ ] En attente de réponse / [ ] Annulé / [ ] Terminé (lots A et B livrés ; solde porté par SILO-110 à SILO-118)

### SILO-110 à SILO-118 : un silo après l'autre sans `BudgetDataModel`
- **Objectif commun** : le service du silo et ses factories n'appellent plus `composeBudgetData()` ni `new BudgetDataModel(...)` ; ils lisent des fragments par reader et construisent directement l'entrée du moteur.
- **Fichiers visés** :
  - SILO-110 : `RetraiteServiceImpl`, `RetirementInputFactory` (**livré** : `RetirementInputFactory.create(RetirementSettingsModel, RetirementModel, List<IncomeModel>, int)` ne connaît plus `BudgetDataModel` ; `RetraiteServiceImpl` lit `RetirementSettingsReader`, `RetirementReader`, `BudgetReader`, `TaxReader` ; il garde `SettingsReader` pour le seul bloc `settings` de la réponse REST, contrat inchangé, retiré avec la composition applicative de ce bloc ; les quatre appelants encore sur `BudgetDataModel` (`OverviewServiceImpl`, `ImpotsServiceImpl`, `TreasuryInputFactory`, `OverviewInputFactory`) extraient les fragments localement jusqu'à leur propre patch ; liste de SILO-002 : 38 → 36) ;
  - SILO-111 : `ImpotsServiceImpl`, `TaxInputFactory`, `TaxSimulationPeriodResolver` ;
  - SILO-112 : `PatrimoineServiceImpl`, `PatrimoineInputFactory`, `PatrimoineMapper` ;
  - SILO-113 : `TresorerieServiceImpl`, `TreasuryInputFactory` ;
  - SILO-114 : `PendingOperationsServiceImpl`, `PointageInputFactory`, `StatementBankImportMapper` ;
  - SILO-115 : `AnalyseServiceImpl`, `AnalyseInputFactory`, `BudgetFacadeView` ;
  - SILO-116 : `AnalysePretsServiceImpl` (dans `server`), `LoanAdviceInputFactory`, `PlacementRateSuggestionInputFactory` ;
  - SILO-117 : `OverviewServiceImpl`, `OverviewInputFactory`, `OverviewMapper` ;
  - SILO-118 : entrées Objectifs et Notifications.
- **Sortie** : le silo ne figure plus dans la liste de SILO-002 ; tests de caractérisation (RF-000) inchangés.
- **Statut** : [x] Non commencé (pour chacun)

### SILO-119 : Import, export et reset par fragments
- **Objectif** : supprimer `GlobalBudgetSnapshotWriter`, `GlobalBudgetSnapshotService.composeSnapshot` et `OverviewMapper.toInternalModel`.
- **Travaux** : chaque silo expose `export()`, `replace(...)`, `reset()` sur ses propres records ; l'application compose et décompose le JSON global et exécute l'import dans une seule transaction (verrou pris en premier, comme aujourd'hui).
- **Sortie** : le format JSON `/budget`, `/budget/import`, `/budget/reset` est inchangé ; plus aucun passage par `BudgetDataModel`.
- **Statut** : [x] Non commencé

### SILO-120 : `BudgetDataModel` supprimé d'`application` et de `server`
- **Travaux** : vérification statique ; la liste de SILO-002 ne contient plus que `persistence` et `transition-snapshot`.
- **Statut** : [x] Non commencé

### SILO-130 à SILO-134 : contrats propres au consommateur (si D1 = non)
- **Objectif** : un silo ne référence plus de type d'un autre silo.
- **Travaux** : le consommateur définit ses entrées (ex. Trésorerie : sorties fiscales, revenus de retraite, flux de placements), l'application traduit à partir des sorties des autres silos. Cinq patchs : Tax←Retirement ; Wealth←Budget ; Treasury←Retirement, Tax, Wealth ; Analysis←Bank, Budget ; Notifications←Goals.
- **Sortie** : `requires` croisés supprimés des `module-info` ; tests de composant inchangés.
- **Statut** : [x] Non commencé

### SILO-140 : Dissoudre `domain-budget`
- **Travaux** : revenus, charges, ponctuels, transferts et variables rejoignent le silo Trésorerie ; les autres silos utilisent leurs types ; éventuel module minimal de valeurs pures (D2).
- **Statut** : [x] Non commencé

### SILO-150 à SILO-159 : séparer API et cœur par silo
- **Objectif** : `X-api` (records, ports, interface de service, `module-info`) et `X-core` (moteur + adaptateurs). SILO-150 est le pilote (Retraite) qui fixe le gabarit ; SILO-151 à SILO-159 le répliquent (Fiscalité, Patrimoine, Trésorerie, Banque/Pointage, Analyse, Crédit, Objectifs, Notifications, Marché).
- **Travaux** : les moteurs implémentent une interface de service ; `DomainEngineConfig` migre vers le futur module de démarrage ; l'application n'instancie plus de moteur.
- **Sortie** : aucune classe `*-core` importée depuis `application`.
- **Statut** : [x] Non commencé

### SILO-160 : `application` ne dépend plus que des `*-api`
- **Travaux** : retirer les dépendances Maven vers les `*-core` ; `module-info` d'`application` limité aux API ; activer la règle correspondante de SILO-003.
- **Statut** : [x] Non commencé

### SILO-170 et SILO-180 : reliquats de `server`
- **SILO-170** : silo Marché (clients CDC, BDF, BCE, snapshots, planificateur) et intégration Enable Banking rattachée au silo Banque (D7) ; absorbe le blocage de MAVEN-101 lot 2 (`PlacementRateSuggestion*`).
- **SILO-180** : dispatch des notifications, canaux Web Push, paramètres et ports de déduplication/abonnements dans le silo Notifications ; absorbe MAVEN-103 lot B2.
- **Statut** : [x] Non commencé

### SILO-190 : Porte A
- **Contrôles** : build complet, ArchUnit, aucune dépendance `application → *-core`, aucun `BudgetDataModel` hors persistance, E2E sans repli.
- **Statut** : [x] Non commencé

### SILO-200 : Module `infra-jpa`
- **Travaux** : configuration Hibernate, source de données, stratégie de nommage (préfixe de table par silo), scan des entités par package de silo ; aucun type métier.
- **Statut** : [x] Non commencé

### SILO-205 : Port `TransactionRunner`
- **Travaux** : interface `TransactionRunner` (par exemple `inTransaction(Supplier<T>)`) dans une API applicative ; implémentation `TransactionTemplate` dans le module de démarrage ; remplacement des `@Transactional` de `ParametersServiceImpl`, `GlobalBudgetSnapshotService` et du reliquat de `server`. Le verrou de mutation reste pris en premier.
- **Sortie** : `application` n'importe plus `org.springframework.transaction` ; `MultiDomainAtomicityTest` (VT-340) inchangé.
- **Statut** : [x] Non commencé

### SILO-206 : Remplacer le verrou global
- **Travaux** : le verrou d'écriture aujourd'hui porté par le cache global et `BudgetMutationLock` devient un verrouillage par silo (optimiste avec `@Version`, ou pessimiste ciblé) ; reprise des tests de concurrence `BudgetCacheStoreConcurrencyTest` et `ConcurrentMutationsApiTest`.
- **Risque** : l'actuel contrat « le cache n'est publié qu'après réussite de la base » (C4) disparaît avec le cache ; la lecture se fait en base.
- **Statut** : [x] Non commencé

### SILO-210 à SILO-217 : persistance de chaque silo dans son module
- **Objectif** : entités, repositories et mappers déplacés de `persistence` vers `X-core`, relation hub supprimée le cas échéant, anciennes tables legacy supprimées.
- **Méthode** (celle de `18-backlog-persistance-patchs.md`) : additif, bascule, suppression ; tests round-trip et redémarrage sur PostgreSQL ; procédure de déploiement documentée (D5).
- **Acquis** : Retraite, Fiscalité, Objectifs, Banque : tables autonomes déjà lues et hub retiré (DB-1100 à DB-1130). Crédit, Patrimoine, Trésorerie : tables autonomes déjà lues, mais les entités legacy du hub (`IncomeEntity`, `ChargeEntity`, `PlacementEntity`, `LoanEntity`…) existent encore (SILO-214, SILO-215, SILO-216 reprennent DB-1140, DB-1150, DB-1160).
- **Statut** : SILO-210 à SILO-213 : [x] Non commencé (acquis partiel : hub déjà retiré) ; SILO-214 à SILO-217 : [x] Non commencé

### SILO-220 : Paramètres chez leurs propriétaires
- **Travaux** : champs de `SettingsEntity` répartis dans les tables de Retraite, Fiscalité, Trésorerie, Simulation et Hypothèses économiques ; `SettingsPersistenceAdapter` supprimé.
- **Statut** : [x] Non commencé

### SILO-230 : Suppression du hub et du modèle global
- **Travaux** : suppression de `BudgetDataEntity`, `BudgetCacheStore`, `PersistenceManager`, `BudgetMutationService`, `BudgetPersistenceGateway`, `EntityModelConverter`, `DomainMutations`, `BudgetDataModel`, du module `transition-snapshot` et des règles ArchUnit associées.
- **Sortie** : `BudgetDataModel` n'existe plus dans le dépôt.
- **Statut** : [x] Non commencé

### SILO-240 : Intégrité inter-silos applicative
- **Travaux** : inventorier les règles aujourd'hui garanties par le hub, le cache ou les mutations génériques (suppression d'un placement référencé par un objectif, un transfert ou un prêt ; catégories ; liens opération en cours ↔ transaction) ; les porter dans `application` avec tests de caractérisation, exécutées dans la même transaction.
- **Risque** : l'inventaire n'est pas encore fait ; c'est le principal inconnu de la phase B.
- **Statut** : [x] Non commencé

### SILO-250 et SILO-290 : vérifications et Porte B
- **Contrôles** : PostgreSQL, redémarrage Spring (VT-320), E2E sans repli (DB-1080), ArchUnit, aucune clé étrangère inter-silos, procédure de déploiement répétée.
- **Statut** : [x] Non commencé

### SILO-300 à SILO-340 : application et web
- **SILO-300** : `application-api` (interfaces de cas d'usage et modèles applicatifs, sans DTO OpenAPI ni `ResponseEntity`), pilote Retraite.
- Le découpage OpenAPI par domaine (RF-C00) est **déjà terminé** pour tous les domaines : aucun patch dédié.
- **SILO-310 à SILO-319** : par fonctionnalité, le contrôleur (qui implémente l'interface `*Api` générée) et le mapper DTO ↔ modèle applicatif passent dans `web` ; le service d'`application` ne renvoie plus de `ResponseEntity`.
- **SILO-320** : module `web` complet (contrôleurs, `GlobalExceptionHandler`, CORS, `HeartbeatController`).
- **SILO-330** : module `bootstrap` (`ServerApplication`, `DesktopLauncher`, configuration, câblage des implémentations, `TransactionRunner`).
- **SILO-340** : `module-info` sur `application-*` et `web`, règles de visibilité complètes. **SILO-350** : CI sélective. **SILO-390** : Porte C (E2E, Docker, matrice de dépendances).
- **Statut** : [x] Non commencé

## 9. Risques et points d'attention

1. **Volume** : environ 60 patchs ; la phase C est mécanique, la phase B porte le risque (schéma, concurrence, intégrité).
2. **Intégrité inter-silos** (SILO-240) : inconnue la plus importante ; à inventorier tôt, avant SILO-216.
3. **Moteurs instanciés par `new`** dans `application` : le passage par interfaces de service (SILO-150 à SILO-160) touche beaucoup de tests de composant.
4. **`module-info` et Spring/Hibernate** : sur le classpath du jar exécutable il est ignoré ; la garantie réelle vient du graphe Maven et d'ArchUnit (D3).
5. **Déploiement** : tant que `ddl-auto: update` est utilisé, chaque patch qui supprime une table impose la procédure manuelle (export JSON, suppression du schéma, réimport) ; à regrouper pour limiter les interventions.
6. **Pas de Maven côté Claude** : les patchs sont validés par `git apply --check` et compilation partielle ; la CI reste l'arbitre.

## 10. Sort des backlogs et documents existants

Principe : **on ne supprime aucun fichier.** Les backlogs `15` à `20` portent l'historique des décisions, les procédures de
déploiement, les noms de tests et la méthode (additif, bascule, suppression) que les patchs `SILO-xxx` réutilisent. Un item non
terminé n'est pas poursuivi tel quel : il est soit **absorbé** par un patch `SILO-xxx` (et marqué « Annulé, remplacé par SILO-xxx »
dans son fichier, patch SILO-001), soit **poursuivi** parce qu'il reste nécessaire. Les travaux « Démarré » par d'autres agents
(MAVEN-101, MAVEN-103) ne sont pas interrompus : ils vont à leur terme ou sont repris par le patch indiqué.

| Fichier | Item non terminé | Décision | Où il est repris |
|---|---|---|---|
| `15-backlog-patchs.md` | RF-B01, RF-C00 | déjà `Terminé` (sous-listes complètes) : rien à faire | — |
| `15-backlog-patchs.md` | RF-D00 (revue de la checklist Maven) | **annulé** : Maven est déjà ouvert | SILO-390 (Porte C) |
| `17-backlog-tests-patchs.md` | aucun (18 patchs `Terminé`) | conservé comme filet de sécurité ; les tests VT sont réutilisés par les portes | SILO-190, SILO-250, SILO-290 |
| `18-backlog-persistance-patchs.md` | DB-1080 (E2E après bascule JPA) | absorbé | SILO-250 |
| | DB-1140, DB-1150, DB-1160 (hubs Crédit, Patrimoine, Trésorerie) | **poursuivis sous un autre ID** : même méthode, mais exécutés avec le déplacement de l'entité dans son silo | SILO-214, SILO-215, SILO-216 |
| | DB-1170 (converters par domaine) | absorbé : les mappers vivent dans chaque silo | SILO-210 à SILO-217 |
| | DB-1180 (réduire `BudgetDataModel`) | **annulé, contredit l'objectif** : il le conserve ; remplacé par la suppression | SILO-230 |
| | DB-1190 (nettoyage legacy) | absorbé | SILO-230 |
| | DB-1200 (gate persistance avant Maven) | absorbé (Maven est déjà ouvert) | SILO-290 |
| `19-backlog-pre-maven-patchs.md` | GATE-010 | absorbé | SILO-190 et SILO-290 |
| `19-inventaire-budget-data-model.md` | document de référence | conservé : c'est la liste de départ de SILO-002 et SILO-120 ; supprimable avec SILO-230 | SILO-002, SILO-120, SILO-230 |
| `20-backlog-modules-maven-patchs.md` | MAVEN-101 lot 2 (`PlacementRateSuggestionInputFactory`) | absorbé : l'arbitrage d'ownership est la décision D7 | SILO-116, SILO-170 |
| | MAVEN-103 lot B2 (notifications, `marketdata`, `enablebanking`) | absorbé | SILO-170, SILO-180 |
| | MAVEN-130 (`server-app`) | remplacé par `web` + `bootstrap` | SILO-320, SILO-330 |
| | MAVEN-140 (garde-fous Maven) | repris | SILO-003, SILO-340 |
| | MAVEN-150 (CI sélective) | repris à l'identique | SILO-350 |
| | MAVEN-160 (revue multi-repo) | reporté : à reconsidérer seulement après SILO-390 ; plus de lien avec DB-1180 | — |
| `14-checklist-maven.md`, `16-tests.md` | documents de référence | conservés ; la checklist devient la base des portes A, B, C | SILO-190, SILO-290, SILO-390 |
| `00` à `13` | principes et documents par domaine | conservés ; `00`, `12`, `13` révisés par SILO-001 | SILO-001 |

Après SILO-390, les backlogs `15` à `20` peuvent être regroupés sous un dossier `archive/` (liens relatifs à corriger) ; seul le
fichier `21` reste vivant.

## 11. Reprise (bloc de synthèse)

- **Tâche** : atteindre des silos isolés, une persistance par silo transactionnelle, et une séparation application/web.
- **Acquis** : domaines purs sans `BudgetDataModel` ; ports Reader/Writer pour sept silos ; hubs Retraite, Fiscalité, Objectifs, Banque retirés ; écriture des paramètres distribuée ; `application` sans dépendance vers `persistence`.
- **Piste en cours** : décisions D1 à D8 tranchées (SILO-000 terminé).
- **Reste à faire** : tout le reste de la section 7 ; SILO-000, SILO-001, SILO-002 et SILO-003 sont terminés ; SILO-100 lots A et B et SILO-110 livrés ; patch maintenant disponible : SILO-111 à SILO-118 (sur les ports propriétaires).
- **Instruction de reprise** : cloner `main`, relire ce fichier, annoncer le patch visé, puis livrer le `.patch` en LF sans attendre de validation (un arbitrage non tranché rend le patch « bloqué »).
