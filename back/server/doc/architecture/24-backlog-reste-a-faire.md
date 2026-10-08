# 24 — Backlog du reste à faire

Statut : 🟡 premier jet (établi le 6 octobre 2026 à partir de `22-etat-reel-main.md` et `23-registre-decisions-actives.md`)

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
| V-01 | Confirmer en CI les tests livrés sans exécution (concurrence et redémarrage Objectifs et Banque) | — | — | À faire | | | SILO-212 B2, SILO-213 B et B2 |

### Transverse

| ID | Patch | Dépend de | Bloque | Statut | Qui | Branche / commit | Réf. 21 |
|---|---|---|---|---|---|---|---|
| R-01 | Introduire Liquibase : état de départ du schéma actuel, passage de `ddl-auto: update` à `validate`, reprise de `LegacySchemaCleanup` (suppression de clés étrangères au démarrage) en script (DA-03) | — | R-51, R-61 | À faire | | | SILO-200 (reste), D5 |
| R-02 | Sortir de `server` et d'`application` les stores de paramètres Crédit et Objectifs : `LoanAdviceSettingsStore`, `LoanAdviceSettingsService`, `LoanAdviceSettingsCodec`, `JpaLoanAdviceSettingsStore` vers `credit-api`/`credit-core` ; `ObjectifsSettingsStore` (port dans `application.settings`) et `JpaObjectifsSettingsStore` vers `goals-api`/`goals-core` | — | R-91 | À faire | | en cours d'implémentation | SILO-214 A, SILO-217 (reste) |

### Persistance : lots B (sortie du cache global)

Un lot B suit la forme de DA-08 et DA-09 (voir section 3). Un lot B2 ajoute les tests de round-trip, de concurrence (verrou du silo)
et de redémarrage sur PostgreSQL.

| ID | Patch | Dépend de | Bloque | Statut | Qui | Branche / commit | Réf. 21 |
|---|---|---|---|---|---|---|---|
| R-11 | Crédit, lot B2 : tests de concurrence (verrou du silo Crédit) et de redémarrage sur PostgreSQL ; le test du store (`JpaLoanStoreTest`) et le round-trip H2 sont livrés avec R-10 | — | R-71 | Fait ! | | 5bb19cc33836dd4974caf9aa3aebd758f5fbdb22 | SILO-214 B2 |
| R-20 | Trésorerie, lot B1 : store dans `treasury-core` pour revenus, charges, ponctuels, variables **et virements** (DA-14) ; retrait de `BudgetPersistenceAdapter`, `TresoreriePersistenceAdapter`, `syncCashflow`, des sept relations du hub concernées, de `TresorerieFieldUpdateDispatcher` et des `updater/*` ; le port `BudgetReader` quitte `transition-snapshot` ; Patrimoine ne lit plus `cashflow_transfer` (l'application lui fournit les virements) | — | R-21, R-30, R-60 | À faire | | | SILO-216 B, DB-1160 |
| R-21 | Trésorerie, lot B2 : paramètres (`cashflow_settings` : pivot, solde de départ, sweep, seuils de cash) écrits directement et lus par un reader du silo ; retrait de sa part de `syncSettings` (DA-05) | R-20 | R-51 | À faire | | | SILO-216 B, D9 |
| R-22 | Trésorerie, lot B3 : tests | R-20, R-21 | R-71 | À faire | | | SILO-216 B2 |
| R-30 | Patrimoine, lot B : store dans `wealth-core` pour placements (et historique), immobilier, catégories d'actifs ; retrait de `PatrimoinePersistenceAdapter`, de `syncWealth` et des relations du hub concernées ; ne touche plus aux virements | R-20 | R-31, R-60 | À faire | | | SILO-215 B, DB-1150 |
| R-31 | Patrimoine, lot B2 : tests | R-30 | R-71 | À faire | | | SILO-215 B2 |
| R-40 | Retraite, lot B : écriture directe du plan de retraite et de `pension_settings` (année de naissance, âge de départ) sous `SiloMutationLock`, reader du silo dans `retirement-core`, retrait de `RetirementPersistenceAdapter`, de `syncPension` et de sa part de `syncSettings` (DA-05) | — | R-41, R-51, R-60 | En cours | | à renseigner (dernier commit de `main` : « lot B de nouveau démarré ») | SILO-210 B |
| R-41 | Retraite, lot B2 : tests | R-40 | R-71 | À faire | | | SILO-210 B2 |
| R-42 | Fiscalité, lot B : écriture directe des enfants, barème, surcharges et de `fiscal_settings` (âge de sortie des enfants, abattement), retrait de `TaxPersistenceAdapter`, de `syncFiscal` et de sa part de `syncSettings` (DA-05) | — | R-43, R-51, R-60 | À faire | | | SILO-211 B |
| R-43 | Fiscalité, lot B2 : tests | R-42 | R-71 | À faire | | | SILO-211 B2 |
| R-51 | Retirer `SettingsEntity`, `SettingsPersistenceAdapter`, `SettingsRepository` et `syncSettings` ; script Liquibase : copie du PASS vers `pension_plan` là où il est vide, puis suppression des 15 colonnes (DA-15) | R-01, R-21, R-40, R-42 | R-61 | À faire | | | SILO-220 B2 |
| R-52 | Retirer `SettingsReader`, `SettingsModelAssembler` et `SettingsModel` des 12 fichiers d'`application` : le bloc `settings` des réponses REST est composé à partir des ports des propriétaires (DA-12) | — | R-61 | À faire | | | SILO-100 (solde), SILO-120 (reste) |
| R-60 | Retirer `GlobalCacheLockRelay` et `BudgetCacheStore.mutationLock` : plus aucune écriture ne passe par le modèle complet | R-20, R-30, R-40, R-42 | R-61 | À faire | | | SILO-206 B |
| R-61 | Supprimer le hub et le modèle global : `BudgetDataEntity`, entités legacy, `BudgetCacheStore`, `PersistenceManager`, `BudgetMutationService`, `BudgetPersistenceGateway`, `EntityModelConverter`, `DomainMutations`, `LegacyObjectifAllocationMigrator` (DA-04), `BudgetDataModel` et le module `transition-snapshot`, fixtures de tests comprises ; script Liquibase de suppression des tables legacy | R-01, R-51, R-52, R-60 | R-70 | À faire | | | SILO-230, DB-1170 à DB-1190 |

### Porte B

| ID | Patch | Dépend de | Bloque | Statut | Qui | Branche / commit | Réf. 21 |
|---|---|---|---|---|---|---|---|
| R-70 | Vérifications PostgreSQL, redémarrage, E2E sans repli ; test automatique du préfixe de table par silo et de l'absence de clé étrangère entre silos (DA-02) ; procédure de déploiement répétée | R-61 | R-71 | À faire | | | SILO-250 |
| R-71 | Porte B : persistance par silo | R-11, R-22, R-31, R-41, R-43, R-70 | R-80 à R-88 (voir § 4) | À faire | | | SILO-290 |

### Application et web

| ID | Patch | Dépend de | Bloque | Statut | Qui | Branche / commit | Réf. 21 |
|---|---|---|---|---|---|---|---|
| R-80 | Cas d'usage Fiscalité (`application-api`) | R-71 | R-90 | À faire | | | SILO-311 |
| R-81 | Cas d'usage Patrimoine | R-71 | R-90 | À faire | | | SILO-312 |
| R-82 | Cas d'usage Trésorerie | R-71 | R-90 | À faire | | | SILO-313 |
| R-83 | Cas d'usage Banque et Pointage (import, opérations en attente, pointage) | R-71 | R-90 | À faire | | | SILO-314 |
| R-84 | Cas d'usage Analyse | R-71 | R-90 | À faire | | | SILO-315 |
| R-85 | Cas d'usage Overview | R-71 | R-90 | À faire | | | SILO-316 |
| R-86 | Cas d'usage Prêts et taux de marché (aujourd'hui dans `server`) | R-71 | R-90 | À faire | | | SILO-317 |
| R-87 | Cas d'usage Notifications (aujourd'hui dans `server`) | R-71 | R-90 | À faire | | | SILO-318 |
| R-88 | Cas d'usage Paramètres et Système | R-71, R-52 | R-90 | À faire | | | SILO-319 |
| R-90 | Module `web` : contrôleurs (dont Retraite), mappers DTO, `GlobalExceptionHandler`, CORS, `HeartbeatController` ; plus aucun `ResponseEntity` dans `application` | R-80 à R-88 | R-91 | À faire | | | SILO-320 |
| R-91 | Module `bootstrap` : démarrage, câblage des implémentations, planificateurs (`MarketDataRefreshScheduler`, `EnableBankingSyncScheduler`), `NotificationBudgetMutationListener`, `TransactionRunner`, `SiloMutationLockRegistry`, `DesktopLauncher` | R-90, R-02, R-71 | R-92 | À faire | | | SILO-330 |
| R-92 | `module-info` sur `application-*` et `web`, règles de visibilité complètes (DA-01) | R-91 | R-93 | À faire | | | SILO-340 |
| R-93 | CI sélective par sous-graphe | R-92 | R-94 | À faire | | | SILO-350 |
| R-94 | Porte C : état cible vérifié (E2E, Docker, matrice de dépendances) | R-93 | — | À faire | | | SILO-390 |

## 3. Critères d'acceptation d'un lot B

À copier dans la consigne de chaque agent qui prend un lot B :

1. Écriture directe dans les tables du silo, sous `TransactionRunner` et `SiloMutationLock` du silo ; ni `@Transactional` ni verrou dans le store (DA-08).
2. Toute écriture qui réécrit une collection entière lit, modifie et écrit **sous le verrou**, par une fonction de modification (DA-09).
3. Un événement de mutation du silo est publié après chaque écriture et écouté par `NotificationBudgetMutationListener` (DA-08).
4. La part du silo disparaît de `syncSettings` et des synchronisations du hub ; la liste fermée de `BudgetDataModelAllowList` perd la ligne de l'adaptateur retiré.
5. Si le patch change un schéma ou un contenu de façon automatique, il le fait par un script Liquibase (DA-03, nécessite R-01) ; sinon il le dit dans sa description.
6. Contrat REST inchangé (DA-13) ; les références inter-silos restent tolérantes (DA-11).

## 4. Parallélisation et points ouverts

**Lancer en parallèle dès maintenant** : R-01 (hors chemin critique des lots B, mais bloque R-51 et R-61), R-02, R-11, R-20, R-40
(déjà en cours), R-52, R-72, V-01. Les lots B de silos distincts ne partagent pas de table, mais ils modifient tous
`BudgetPersistenceGateway`, `PersistenceManager`, `BudgetMutationService` et `DomainMutations` : prévoir des conflits de fusion et
fusionner un lot à la fois, CI verte avant le suivant (DA-07).

**Chemin critique** : R-20 → R-30 ; R-21 ; R-40 et R-42 → R-51 → R-61 → R-70 → R-71 → R-80 à R-88 → R-90 → R-91 → R-92 → R-93 → R-94.

**Points ouverts**

1. **Forme de Liquibase (R-01)** : recommandation, un fichier maître dans le composition root et des scripts par silo dans les
   ressources de chaque `*-core`. À valider dans le patch.
2. **Lot B de Retraite déjà en cours (R-40)** : il a été démarré avant DA-03 ; vérifier qu'il respecte la liste de la section 3.
   Aucun script n'est nécessaire pour `pension_settings` si la version qui alimente les tables de paramètres (SILO-220 B1) a
   tourné au moins une fois sur chaque base ; sinon, une copie scriptée est requise.
3. **Dépendance de R-80 à R-88 envers R-71** : héritée du document 21 (« SILO-290 pour le reste »). Ces cas d'usage ne touchent pas
   à la persistance ; si la dépendance n'est pas voulue, ils peuvent démarrer plus tôt, ce qui raccourcit le chemin critique.
4. **Propriétaire des suggestions de taux** (`PlacementRateSuggestion*`, `PlacementRateSuggestionService`, dans `server`) :
   aucune décision ; à trancher avant R-86 et R-91.
5. **Granularité de R-20** : le lot Trésorerie est le plus gros (sept relations du hub, virements, `TresorerieFieldUpdateDispatcher`
   et dix fichiers d'`updater`). Si l'agent le juge trop large, il le scinde lui-même et ajoute les lignes ici avant de commencer.
