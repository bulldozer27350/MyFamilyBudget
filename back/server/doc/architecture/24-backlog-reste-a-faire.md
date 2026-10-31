# 24 — Backlog du reste à faire

Statut : ✅ backlog soldé (établi le 6 octobre 2026 à partir de `22-etat-reel-main.md` et `23-registre-decisions-actives.md`, clos le 31 octobre 2026 — toutes les lignes sont Fait !, y compris la vérification exploitant sur le mini-PC)

Ce document est **le seul endroit où l'on suit l'avancement**. Les décisions sont dans `23`, l'état du code dans `22`, l'historique
dans `21` (figé). Chaque ligne du tableau est dérivée du code (section 2 de `22`), pas du récit du document 21.

## 1. Mode d'emploi

- Une ligne = un patch mergeable seul. Un statut unique : **À faire**, **En cours**, **Livré** (une ligne livrée passe dans
  `22` ou disparaît ; elle ne reste pas ici).
- **Dépend de** : lignes à livrer avant. **Bloque** : lignes qui ne peuvent pas démarrer sans celle-ci.
- **Qui** et **Branche / commit** : à renseigner par vous dès qu'un agent prend un patch. Ils sont vides tant que personne ne l'a pris.
- **Réf. 21** : ancien identifiant, pour retrouver le contexte dans l'archive.
- Un patch qui rencontre un arbitrage non tranché devient « bloqué » et s'arrête (DA-13).

## 2. Backlog

### Vérification

| ID | Patch | Dépend de | Bloque | Statut | Qui | Branche / commit | Réf. 21 |
|---|---|---|---|---|---|---|---|
| V-01 | Confirmer en CI les tests livrés sans exécution (concurrence et redémarrage Objectifs et Banque) | — | — | Fait ! | Exploitant | — | SILO-212 B2, SILO-213 B et B2 |

### Transverse

| ID | Patch | Dépend de | Bloque | Statut | Qui | Branche / commit | Réf. 21 |
|---|---|---|---|---|---|---|---|
| R-01 | Introduire Liquibase : état de départ du schéma actuel, passage de `ddl-auto: update` à `validate`, retrait de `LegacySchemaCleanup` (DA-03). Lot A livré (câblage inactif). Lot B livré en « reset propre » plutôt qu'en reverse-engineering de l'existant (DA-16) : baseline générée sur H2 et PostgreSQL (localement, l'agent n'a pas Maven), 20 tables orphelines écartées (divergence H2/PostgreSQL et noms pré-renommage de silo), 36 tables réparties dans les 11 changelogs par silo (zéro FK inter-silo), `enabled: true`/`ddl-auto: validate` basculés (profils par défaut et `docker`), `LegacySchemaCleanup` retiré. **Côté exploitant** : export JSON + `pg_dump` de sauvegarde, base PostgreSQL de prod vidée, déploiement de cette image, réimport JSON — procédure détaillée dans l'échange agent du 20 oct. 2026 et DA-16 — **exécutée et vérifiée sur le mini-PC** | — | R-51, R-61 | Fait ! | Agent / Exploitant | — | SILO-200 (reste), D5 |
| R-02 | Sortir de `server` et d'`application` les stores de paramètres Crédit et Objectifs : `LoanAdviceSettingsStore`, `LoanAdviceSettingsService`, `LoanAdviceSettingsCodec`, `JpaLoanAdviceSettingsStore` vers `credit-api`/`credit-core` ; `ObjectifsSettingsStore` (port dans `application.settings`) et `JpaObjectifsSettingsStore` vers `goals-api`/`goals-core` | — | R-91 | Fait ! | Agent | — | SILO-214 A, SILO-217 (reste) |

### Persistance : lots B (sortie du cache global)

Un lot B suit la forme de DA-08 et DA-09 (voir section 3). Un lot B2 ajoute les tests de round-trip, de concurrence (verrou du silo)
et de redémarrage sur PostgreSQL.

| ID | Patch | Dépend de | Bloque | Statut | Qui | Branche / commit | Réf. 21 |
|---|---|---|---|---|---|---|---|
| R-11 | Crédit, lot B2 : tests de concurrence (verrou du silo Crédit) et de redémarrage sur PostgreSQL ; le test du store (`JpaLoanStoreTest`) et le round-trip H2 sont livrés avec R-10 | — | R-71 | Fait ! | | 5bb19cc33836dd4974caf9aa3aebd758f5fbdb22 | SILO-214 B2 |
| R-20 | Trésorerie, lot B1 : store dans `treasury-core` pour revenus, charges, ponctuels, variables **et virements** (DA-14) ; retrait de `BudgetPersistenceAdapter`, `TresoreriePersistenceAdapter`, `syncCashflow`, des sept relations du hub concernées, de `TresorerieFieldUpdateDispatcher` et des `updater/*` ; le port `BudgetReader` quitte `transition-snapshot` ; Patrimoine ne lit plus `cashflow_transfer` (l'application lui fournit les virements) | — | R-21, R-30, R-60 | Fait ! | Agent | refactor/r-20-treasury-lot-b1 | SILO-216 B, DB-1160 |
| R-21 | Trésorerie, lot B2 : paramètres (`cashflow_settings` : pivot, solde de départ, sweep, seuils de cash) écrits directement et lus par un reader du silo ; retrait de sa part de `syncSettings` (DA-05) | R-20 | R-51 | Fait ! | | constaté livré par R-20 (947a176) : `JpaTreasuryStore.updateTresorerieSetting` écrit `cashflow_settings` sous verrou/transaction du silo (`TresorerieCommandService`), `getTresorerieSettings()` est le reader `@Primary`, `BudgetPersistenceGateway.syncSettings` n'écrit plus ces champs (migration one-shot au démarrage dans `migrateCashflowSettingsIfMissing`) | SILO-216 B, D9 |
| R-22 | Trésorerie, lot B3 : tests | R-20, R-21 | R-71 | Fait ! (constaté déjà livré, commit 50f0717, doc non mis à jour à l'époque) | | 50f0717 | SILO-216 B2 |
| R-30 | Patrimoine, lot B : store dans `wealth-core` pour placements (et historique), immobilier, catégories d'actifs ; retrait de `PatrimoinePersistenceAdapter`, de `syncWealth` et des relations du hub concernées ; ne touche plus aux virements | R-20 | R-31, R-60 | Fait ! (nettoyage complété par R-30b) | | | SILO-215 B, DB-1150 |
| R-30b | Patrimoine, suite du nettoyage de R-30. **Livré (arbitrage du 14/10/2026, option B appliqué)** : `WriteFailureKeepsMemoryTest` (VT-330) migré de Patrimoine vers Retraite (`pensionPlanRepository`/`syncPension`, qui écrit déjà inconditionnellement à chaque `save` — plus besoin d'un second "seed" comme avec les placements) ; `BudgetMutationService`/`DomainMutations.savePatrimoineRow`/`deletePatrimoineRow`/`addAssetCategory` retirés (code mort confirmé, plus aucun appelant) ; `RealEstateEntity`/`RealEstateRepository` et `AssetCategoryEntity`/`AssetCategoryRepository` supprimés ; `BudgetDataEntity` ne garde que la relation `placements`. `PlacementEntity`/`PlacementRepository`/`BudgetDataEntity.placements`, dernier témoin du hub pour `BudgetPersistenceGatewayRepeatedSaveTest` (SILO-119 B2), conservés jusqu'à R-61 (option retenue sur le point ouvert signalé ici) puis supprimés en bloc par R-61-24 avec le reste du module `persistence` ; script Liquibase de suppression des tables legacy du hub livré par R-61-26 | R-30 | R-60 | Fait ! | Agent | refactor/r-30b-patrimoine-nettoyage | SILO-215 B (reste) |
| R-31 | Patrimoine, lot B2 : tests | R-30 | R-71 | Fait ! (placements et immobilier ; virements DA-14 et catégories d'actifs hors scope) | | | SILO-215 B2 |
| R-40 | Retraite, lot B : écriture directe du plan de retraite et de `pension_settings` (année de naissance, âge de départ) sous `SiloMutationLock`, reader du silo dans `retirement-core`, retrait de `RetirementPersistenceAdapter`, de `syncPension` et de sa part de `syncSettings` (DA-05) | — | R-41, R-51, R-60 | Fait ! | | | SILO-210 B |
| R-41 | Retraite, lot B2 : tests | R-40 | R-71 | Fait ! | | | SILO-210 B2 |
| R-42 | Fiscalité, lot B : écriture directe des enfants, barème, surcharges et de `fiscal_settings` (âge de sortie des enfants, abattement), retrait de `TaxPersistenceAdapter`, de `syncFiscal` et de sa part de `syncSettings` (DA-05) | — | R-43, R-51, R-60 | Fait ! | | | SILO-211 B |
| R-43 | Fiscalité, lot B2 : tests | R-42 | R-71 | Fait ! | | | SILO-211 B2 |
| R-51 | Retirer `SettingsEntity`, `SettingsPersistenceAdapter`, `SettingsRepository` et `syncSettings` ; script Liquibase : copie du PASS vers `pension_plan` là où il est vide, puis suppression des 15 colonnes (DA-15) | R-01, R-21, R-40, R-42 | R-61 | Fait ! | | | SILO-220 B2 |
| R-52 | Retirer `SettingsReader`, `SettingsModelAssembler` et `SettingsModel` des 12 fichiers d'`application` : le bloc `settings` des réponses REST est composé à partir des ports des propriétaires (DA-12) | — | R-61 | Fait ! | | | SILO-100 (solde), SILO-120 (reste) |
| R-60 | Retirer `GlobalCacheLockRelay` et `BudgetCacheStore.mutationLock` : plus aucune écriture ne passe par le modèle complet | R-20, R-30, R-40, R-42 | R-61 | Fait ! | | | SILO-206 B |
| R-61 | Supprimer le hub et le modèle global : `BudgetDataEntity`, entités legacy, `BudgetCacheStore`, `PersistenceManager`, `BudgetMutationService`, `BudgetPersistenceGateway`, `EntityModelConverter`, `DomainMutations`, `LegacyObjectifAllocationMigrator` (DA-04), `BudgetDataModel` et le module `transition-snapshot`, fixtures de tests comprises ; script Liquibase de suppression des tables legacy. **Découpé le 25 oct. 2026 en 27 sous-patchs mergeables séparément** (46 fichiers de test et ~20 fichiers de production concernés, trop pour un seul patch vérifiable sans compilateur côté agent) : voir `26-sequencage-r61-suppression-hub.md` (désormais clos) pour la liste complète, l'ordre et la méthode. **Fait au sous-patch R-61-27** : les 27 sous-patchs (R-61-01 à R-61-27) sont livrés et intégrés ; modules `persistence` et `transition-snapshot` supprimés du reactor ; script Liquibase de suppression des 4 tables legacy ajouté (R-61-26) ; DA-04 et DA-12 archivées en section 4 de `23-registre-decisions-actives.md` | R-01, R-51, R-52, R-60 | R-70 | Fait ! | Agent | — | SILO-230, DB-1170 à DB-1190 |

### Porte B

| ID | Patch | Dépend de | Bloque | Statut | Qui | Branche / commit | Réf. 21 |
|---|---|---|---|---|---|---|---|
| R-70 | Vérifications PostgreSQL, redémarrage, E2E sans repli ; test automatique du préfixe de table par silo et de l'absence de clé étrangère entre silos (DA-02) ; procédure de déploiement répétée. **Livré** : `SiloTablePrefixArchTest` (nouveau, `server.internal.architecture`), le test manquant que DA-02 notait explicitement comme absent — vérifié par simulation hors Maven (32 entités JPA des 10 silos à persistance propre, tous les préfixes de table réels relevés et figés par silo, 0 écart, 0 collision entre silos). L'absence de clé étrangère inter-silos était déjà couverte par `SiloPersistenceIsolationArchTest` (SILO-21x), simplement non documentée comme réponse à DA-02 : corrigé dans `23-registre-decisions-actives.md`. `RestartPersistenceTest` (VT-320) et la suite E2E (`tests/e2e`, dont `no-fallback.spec.js`) relus : aucune référence au hub/`PersistenceManager`/`BudgetDataModel`, rien à corriger côté code pour R-61. **Reste** (vérifications d'exécution réelle, hors portée d'un agent sans Maven/Docker/PostgreSQL) : confirmé par l'exploitant (27/10/2026) — CI verte (`RestartPersistenceTest` en PostgreSQL, suite E2E sans repli) et données réelles valides en production après la procédure de déploiement répétée sur le mini-PC | R-61 | R-71 | Fait ! | Agent / Exploitant | — | SILO-250 |
| R-71 | Porte B : persistance par silo. **Fait** (constat de porte, 27 oct. 2026, aucune décision nouvelle) : relecture de DA-01 à DA-07 dans `23-registre-decisions-actives.md` — DA-02 (préfixe de table, pas de FK inter-silo), DA-03 (migrations par Liquibase, `ddl-auto: validate` actif, forme du changelog confirmée), DA-05 (paramètres portés par chaque lot B), DA-06 (`simulateUntilAge`/`inflationRate` dans le silo Paramètres) et DA-07 (sortie du cache global) sont toutes appliquées et verrouillées par un test ou par la structure du code (le hub n'existe plus) ; déplacées en section 4 du doc 23. DA-01 (`module-info`) reste active en section 3 : elle ne contraint que `web`/`bootstrap`/`application-core` (R-80 à R-94), pas encore atteints, ce n'est pas un écart. Écart trouvé au passage et corrigé : `tests/gate/check-gate.js` référençait encore les modules `persistence`/`transition-snapshot` (pom.xml dans `POMS`, exemption `BUDGET_DATA_MODEL_MODULES`) supprimés par R-61 ; sans impact fonctionnel (`fs.existsSync`/`fs.readdirSync` les ignoraient déjà silencieusement) mais nettoyé pour exactitude ; `node tests/gate/check-gate.js static` rejoué en local (sparse-checkout, hors CI) : vert | R-11, R-22, R-31, R-41, R-43, R-70 | R-80 à R-88 (voir § 4) | Fait ! | Agent | — | SILO-290 |

### Application et web

| ID | Patch | Dépend de | Bloque | Statut | Qui | Branche / commit | Réf. 21 |
|---|---|---|---|---|---|---|---|
| R-80 | Cas d'usage Fiscalité (`application-api`) | — | R-90 | Fait ! | Agent | — | SILO-311 |
| R-81 | Cas d'usage Patrimoine | — | R-90 | Fait ! | Agent | — | SILO-312 |
| R-82 | Cas d'usage Trésorerie : extraction de la composition de la réponse (lectures des fragments, construction de l'entrée du moteur, moyennes réelles, suggestions, traduction des types des silos) depuis `TresorerieServiceImpl` vers `TreasuryUseCase` (`application-api`, package `application.usecase.treasury`) / `DefaultTreasuryUseCase`, même gabarit que R-80/R-81 ; `TresorerieServiceImpl` ne garde que la conversion HTTP, `TresorerieMapper` ne connaît plus `domain.treasury.model` (ses anciennes méthodes DTO→modèle de domaine, sans appelant dans le périmètre cloné, sont retirées comme pour R-81) | — | R-90 | Fait ! | Agent | — | SILO-313 |
| R-83 | Cas d'usage Banque et Pointage (import, opérations en attente, pointage) : extraction de la composition des réponses (import bancaire, opérations en attente, pointage) et des écritures sous verrou (`BankImportCommandService`) depuis `StatementBankImportServiceImpl`/`PendingOperationsServiceImpl`/`PointageServiceImpl` vers `BankPointageUseCase` (`application-api`, package `application.usecase.bankpointage`) / `DefaultBankPointageUseCase`, même esprit que R-80 à R-82 ; contrairement à la Trésorerie, aucun modèle applicatif dédié n'est introduit (le silo n'exposait déjà que des `Map<String, Object>` au-delà de la frontière REST) ; les anciens 404/400 construits en ligne (ligne introuvable, liste inconnue) deviennent `BankLigneNotFoundException`/`IllegalArgumentException`, traduits par les contrôleurs ; `convertExcelToCsv` (utilitaire de format pur, sans lecture/écriture de silo) reste porté par `StatementBankImportServiceImpl` | — | R-90 | Fait ! | Agent | — | SILO-314 |
| R-84 | Cas d'usage Analyse | — | R-90 | Fait ! | Agent | — | SILO-315 |
| R-85 | Cas d'usage Overview : extraction de la composition de la réponse (lectures des fragments, construction de l'entrée du moteur Overview, traduction des types vers application-api) depuis `OverviewServiceImpl` vers `OverviewUseCase` (`application-api`, package `application.usecase.overview`) / `DefaultOverviewUseCase` ; `OverviewServiceImpl` ne garde que la conversion HTTP et la composition du budget réexposé (`BudgetFacadeView`) ; contrat REST inchangé | — | R-90 | Fait ! | Agent | — | SILO-316 |
| R-86 | Cas d'usage Prêts et taux de marché : extraction de la composition des réponses d'analyse des prêts (résolution du taux de marché, construction de l'entrée du moteur d'analyse, appel du moteur, traduction des types vers `application-api`) et des suggestions de taux depuis `AnalysePretsServiceImpl` et `SuggestionsTauxServiceImpl` vers `LoanMarketUseCase` (`application-api`, package `application.usecase.loanmarket`) / `DefaultLoanMarketUseCase` ; `PlacementRateSuggestion*` déplacé de `server` vers `application.rate` et `application.factory` avec dépendance `market-api` dans `application/pom.xml` ; port `LoanAdviceSettingsPort` dans `application.settings` isolant l'accès aux paramètres de prêt ; `AnalysePretsServiceImpl` et `SuggestionsTauxServiceImpl` ne gardent que la conversion HTTP ; contrat REST inchangé | — | R-90 | Fait ! | Agent | — | SILO-317 |
| R-87 | Cas d'usage Notifications : extraction de la gestion des paramètres, du contrôle manuel des alertes et des abonnements push depuis `NotificationsServiceImpl` vers `NotificationsUseCase` (`application-api`, package `application.usecase.notifications`) / `DefaultNotificationsUseCase` ; `NotificationsMapper` migré de `server` vers `application.mapper` ; `NotificationsServiceImpl` ne garde que la conversion HTTP ; contrat REST inchangé | — | R-90 | Fait ! | Agent | — | SILO-318 |
| R-88 | Cas d'usage Paramètres et Système : extraction de l'orchestration des paramètres (composition de la vue, calcul des métadonnées, application atomique des mutations sous verrou et transaction) depuis `ParametersServiceImpl` vers `SettingsUseCase` (`application-api`, package `application.usecase.settings`) / `DefaultSettingsUseCase` ; extraction des opérations transverses budget (export, import, reset) depuis `SystemeServiceImpl` vers `SystemUseCase` (`application-api`, package `application.usecase.system`) / `DefaultSystemUseCase` avec `SystemeMapper` pour isoler `application-api` de tout import DTO OpenAPI/Jackson ; `ParametersServiceImpl` et `SystemeServiceImpl` ne gardent que la conversion HTTP ; contrat REST inchangé | R-52 | R-90 | Fait ! | Agent | — | SILO-319 |
| R-90 | Module `web` : contrôleurs (dont Retraite), mappers DTO, `GlobalExceptionHandler`, CORS, `HeartbeatController` ; plus aucun `ResponseEntity` dans `application` | R-80 à R-88 | R-91 | Fait ! | Agent | — | SILO-320 |
| R-91 | Module `bootstrap` : démarrage, câblage des implémentations, planificateurs (`MarketDataRefreshScheduler`, `EnableBankingSyncScheduler`), `NotificationBudgetMutationListener`, `TransactionRunner`, `SiloMutationLockRegistry`, `DesktopLauncher` | R-90, R-02, R-71 | R-92 | Fait ! | Agent | — | SILO-330 |
| R-92 | `module-info` sur `application-*` et `web`, règles de visibilité complètes (DA-01) | R-91 | R-93 | Fait ! | Agent | — | SILO-340 |
| R-93 | CI sélective par sous-graphe. **Livré** (31/10/2026) : nouveau `tests/gate/select-modules.js` calcule, à partir du diff avec `origin/main` (fourni sur stdin, une ligne par fichier modifié), le sous-ensemble de modules `back/pom.xml` touchés ; `.github/workflows/ci.yml` (seul, `ci-cd.yml` reste une suite complète avant publication de l'image Docker, cf. R-94) lance alors `mvn -f back/pom.xml -pl <modules> -amd -am test` au lieu de systématiquement `-pl server -am` — Maven résout lui-même les dépendances (`-am`) et les dépendants (`-amd`, `server`/`bootstrap` compris dès que l'un d'eux dépend, même en portée test, d'un module sélectionné) ; repli automatique sur la suite complète si un fichier modifié touche `back/pom.xml`, `.github/workflows`, `tests/gate`, `openapi/`, `Dockerfile`, `docker-compose*.yml`, ou un chemin sous `back/` hors de tout module connu ; aucun test backend relancé si seuls `view/`, `tests/e2e` ou de la documentation (`back/<module>/doc/**`) ont changé. `tests/gate/check-gate.js` adapté (nouvelle variable `MFB_GATE_MODULES`, repli par défaut sur `server` seul, comportement inchangé hors mode sélectif) pour ne vérifier les rapports Surefire que des modules effectivement testés dans ce run. Écart corrigé au passage : `POMS` dans `check-gate.js` n'incluait pas `back/bootstrap/pom.xml` (module ajouté par R-91, jamais repris dans ce garde-fou statique). Maven non exécuté dans Claude (réseau restreint) ; `select-modules.js` testé manuellement (plusieurs scénarios) ; `git apply --check` validé sur clone frais `core.autocrlf=true` | R-92 | R-94 | Fait ! | Agent | — | SILO-350 |
| R-94 | Porte C : état cible vérifié (E2E, Docker, matrice de dépendances). **Livré et vérifié par l'exploitant** (31 oct. 2026) : build complet et E2E sans repli verts en CI, matrice de dépendances conforme (`MavenModuleGraphTest`), déploiement Docker confirmé en production sur le mini-PC | R-93 | — | Fait ! | Agent / Exploitant | — | SILO-390 |

## 3. Critères d'acceptation d'un lot B

À copier dans la consigne de chaque agent qui prend un lot B :

1. Écriture directe dans les tables du silo, sous `TransactionRunner` et `SiloMutationLock` du silo ; ni `@Transactional` ni verrou dans le store (DA-08).
2. Toute écriture qui réécrit une collection entière lit, modifie et écrit **sous le verrou**, par une fonction de modification (DA-09).
3. Un événement de mutation du silo est publié après chaque écriture et écouté par `NotificationBudgetMutationListener` (DA-08).
4. La part du silo disparaît de `syncSettings` et des synchronisations du hub ; la liste fermée de `BudgetDataModelAllowList` perd la ligne de l'adaptateur retiré.
5. Si le patch change un schéma ou un contenu de façon automatique, il le fait par un script Liquibase (DA-03, nécessite R-01) ; sinon il le dit dans sa description.
6. Contrat REST inchangé (DA-13) ; les références inter-silos restent tolérantes (DA-11).

## 4. Parallélisation (historique) et points ouverts

**Lancé en parallèle** : R-01 (hors chemin critique des lots B, bloquait R-51 et R-61), R-02, R-11, R-20, R-40, R-52, R-72, V-01,
R-80 à R-88. Les lots B de silos distincts ne partageaient pas de table, mais modifiaient tous `BudgetPersistenceGateway`,
`PersistenceManager`, `BudgetMutationService` et `DomainMutations` (DA-07) ; tous fusionnés, CI verte à chaque étape.

**Chemin critique parcouru** : R-20 → R-30 ; R-21 ; R-40 et R-42 → R-51 → R-61 → R-70 → R-71 → R-90 → R-91 → R-92 → R-93 → R-94.

**Points ouverts**

Aucun. Les 5 points ouverts du premier jet ont été tranchés le 14/10/2026 : forme de Liquibase validée telle que livrée par R-01
lot A (pas de changement) ; avancement de R-40 vérifié (volet écriture restant, note ajoutée à sa ligne) ; dépendance de R-80 à
R-88 envers R-71 retirée (ci-dessus) ; propriétaire des suggestions de taux tranché sur la ligne R-86 ; arbitrage VT-330/R-30b
tranché (option B), livré sur la ligne R-30b.

Le point découvert en livrant R-30b est clos : `BudgetPersistenceGatewayRepeatedSaveTest` (SILO-119 B2), seul témoin restant
utilisant `PlacementEntity`/`PlacementRepository` (hub), a été conservé tel quel jusqu'à R-61 (option retenue), puis supprimé
en bloc avec tout le module `persistence` par R-61-24.

Le point équivalent ouvert en livrant R-40 (lot B) est clos de la même façon : `WriteFailureKeepsMemoryTest` (VT-330), migré
vers les placements en attendant R-61, a été réécrit par R-61-24 contre `patrimoine.getTransfers()` (silo Trésorerie réel via
`PatrimoinePersistenceAdapter`/DA-14) plutôt que contre le cache mort — voir le détail de R-61-24 dans
`26-sequencage-r61-suppression-hub.md`.
