# 22 — État réel de `main` (inventaire)

Statut : 🟢 relevé le 6 octobre 2026 sur `main` @ `d42b2ff`, par lecture du code et des `pom.xml` (aucun build ni test exécuté) ; mis à jour après R-10 (Crédit, lot B)

Ce document **constate** ; il ne planifie rien et ne décide rien. Le récit des patchs reste dans `21-plan-silotage.md` (figé en
archive), les décisions qui contraignent encore le travail sont dans `23-registre-decisions-actives.md`, le reste à faire sera
dérivé de ce document dans un backlog séparé.

Chaque ligne se vérifie par une commande `grep` ou `find` ; en cas de doute entre ce document et le document 21, **ce document
(le code) fait foi**.

## 1. Modules Maven

29 modules dans `back/pom.xml`.

| Famille | Modules |
|---|---|
| Silos (11 × `-api` + `-core`) | `retirement`, `tax`, `wealth`, `bank-pointage`, `treasury`, `analysis`, `credit`, `goals`, `settings`, `notifications`, `market` |
| Infrastructure | `infra-jpa` (nommage Hibernate uniquement) |
| Application | `application-api` (ports transactionnels et cas d'usage Retraite seulement), `application` |
| Reliquats de transition | `transition-snapshot`, `persistence`, `server` |
| OpenAPI généré | `api` |

**Modules cibles absents** : `web`, `bootstrap`, scission `application` / `application-core`. Liquibase (D5) n'est présent ni dans
un `pom.xml` ni dans un `.yml` ; `ddl-auto: update` reste actif dans `application.yml` et `application-docker.yml`.

Arêtes Maven encore transitoires : `persistence` dépend de six `*-core` (`retirement`, `tax`, `goals`, `bank-pointage`,
`wealth`, `treasury`) et de `settings-core` ; `server` dépend de onze cœurs (composition root).

## 2. Persistance, silo par silo

Légende : « entités dans le cœur » = lot A livré (entités, repositories, mappers dans `<silo>-core`, package `core.persistence`) ;
« lot B » = adaptateur du silo écrivant directement dans ses tables, sans passer par le cache global.

| Silo | Entités dans le cœur | Lot B (écriture directe) | Reste dans `persistence` |
|---|---|---|---|
| Objectifs | oui | **fait** (`JpaGoalStore`) | `GoalRepository` relu au chargement du cache, `LegacyObjectifAllocationMigrator` (sans consommateur) |
| Banque | oui | **fait** (`JpaBankStore`) | copie `BudgetDataModel.bankImport` chargée au démarrage (`loadBankImport`), plus écrite ni lue |
| Notifications | oui | n'a jamais eu d'adaptateur dans `persistence` | rien |
| Marché | oui | idem | rien |
| Retraite | oui | **non** | `RetirementPersistenceAdapter` (lecture par repository, écriture par `PersistenceManager`), `syncPension` |
| Fiscalité | oui | **non** | `TaxPersistenceAdapter`, `syncFiscal` |
| Crédit | oui | **fait** (`JpaLoanStore`, R-10) | table legacy `loan` du hub : plus d'entité ni de repository, orpheline en base (suppression par script en R-61) |
| Patrimoine | oui | **non** | `PatrimoinePersistenceAdapter`, `syncWealth`, entités hub `PlacementEntity`, `PlacementHistoryEntryEntity`, `RealEstateEntity`, `AssetCategoryEntity`, `TransferEntity` |
| Trésorerie | oui | **non** | `BudgetPersistenceAdapter` (lecture), `TresoreriePersistenceAdapter` (écriture), `syncCashflow`, entités hub `IncomeEntity`, `ChargeEntity`, `OneOffExpenseEntity`, `VariableIncomeEntity`, `VariableOverrideEntity` |
| Paramètres | tables `pension_settings`, `fiscal_settings`, `cashflow_settings`, `app_settings` créées dans `retirement-core`, `tax-core`, `treasury-core`, `settings-core` | **non** | `SettingsPersistenceAdapter` (lecture du cache), `SettingsEntity`, `syncSettings` |

Les quatre tables de paramètres sont **alimentées** par `BudgetPersistenceGateway.syncSettings` (à chaque `save` et au
chargement) mais **aucun code de production ne les lit** (seuls le gateway et `PersistenceManager` référencent leurs
repositories).

`SettingsEntity` porte encore 15 colonnes métier : `birthYear`, `retireAge` (Retraite) ; `childExitAge`, `taxAbattement`
(Fiscalité) ; `pivotDate`, `pivotMode`, `startBalance`, `sweepEnabled`, `cashCeiling`, `cashFloor`, `cashAlertThreshold`
(Trésorerie) ; `simulateUntilAge`, `inflationRate` (Paramètres) ; `pass2026`, `passGrowthRate` (Retraite : le document 21 indique
que le PASS est porté par `PensionPlanEntity` depuis SET-040 ; **usage des deux colonnes de `SettingsEntity` à vérifier avant
de les compter dans un lot**).

Hub `BudgetDataEntity` : une relation `@OneToOne` (paramètres) et neuf `@OneToMany` (`incomes`, `charges`, `placements`,
`realEstate`, `oneoff`, `transfers`, `variableIncomes`, `variableOverrides`, `assetCategories`).
`BudgetPersistenceGateway.save` réécrit encore ces neuf collections puis appelle cinq synchronisations :
`syncFiscal`, `syncPension`, `syncWealth`, `syncCashflow`, `syncSettings`. `syncGoals`, `syncBankImport` et `syncCreditLoans`
n'existent plus. `BudgetDataModel.loans` n'est plus alimenté par le chargement (liste vide).

### 2.1 Classes globales de `persistence`

Liste fermée de `BudgetDataModelAllowList` : `FROZEN_SIZE = 11`, toutes dans `persistence` (cinq adaptateurs, puis
`BudgetCacheStore`, `BudgetMutationService`, `BudgetPersistenceGateway`, `PersistenceManager`, `EntityModelConverter`,
`TresorerieFieldUpdateDispatcher`).

| Classe | Lignes |
|---|---|
| `BudgetMutationService` | 1137 |
| `BudgetPersistenceGateway` | 599 |
| `EntityModelConverter` | 452 |
| `BudgetCacheStore` | 382 |
| `DomainMutations` | 309 |
| `PersistenceManager` | 266 |
| `LegacySchemaCleanup` (hors liste SILO-002) | 157 |
| adaptateurs (10 fichiers dont `GlobalCacheLockRelay`) | 25 à 167 chacun |
| `updater/*` (10 fichiers) | 29 à 171 chacun |

Les `BudgetDataModel` hors `persistence` sont uniquement dans `transition-snapshot` (définition) et en commentaires ou
imports de types dans les modules d'API et de calcul : la liste fermée, vérifiée par `BudgetDataModelUsageArchTest`, est le
critère.

### 2.2 Verrous et transactions

- Livrés : `TransactionRunner`, `SiloMutationLock`, `MutationSilo` (dans `application-api`), `SpringTransactionRunner`,
  `SiloMutationLockRegistry`, `TransactionConfig` (dans `server`).
- Encore en place : `GlobalCacheLockRelay` (une transaction qui a verrouillé un silo prend ensuite le verrou global du cache).
- Une seule annotation `@Transactional` réelle en production : au niveau classe de `PersistenceManager`. Les autres
  occurrences repérées par `grep` sont des commentaires. `NotificationBudgetMutationListener` porte trois
  `@TransactionalEventListener(AFTER_COMMIT)`.
- Événements : `BudgetMutatedEvent` (`persistence`), `GoalsMutatedEvent` (publié par `JpaGoalStore`), `BankImportMutatedEvent`
  (publié par `JpaBankStore`), `LoansMutatedEvent` (publié par `JpaLoanStore`). `NotificationBudgetMutationListener` écoute
  les quatre.

## 3. Application et web

- `application-api` : un seul cas d'usage, `RetirementUseCase` (lecture et sauvegarde), implémenté par
  `DefaultRetirementUseCase`. Les dix autres fonctionnalités n'ont pas de cas d'usage.
- Contrôleurs : il n'existe pas de module `web`. Onze `*ServiceImpl` d'`application` implémentent les `*Api` générés ;
  `server` en porte cinq autres (`AnalysePretsServiceImpl`, `EnableBankingApiServiceImpl`, `NotificationsServiceImpl`,
  `SuggestionsTauxServiceImpl`, `TauxMarcheServiceImpl`) plus `HeartbeatController` et `GlobalExceptionHandler`.
- Douze fichiers d'`application` importent Spring Web (`ResponseEntity`) ; treize mappers dans `application.mapper`.
- `SettingsReader` : douze fichiers dans `application` (huit `*ServiceImpl`, `NotificationCheckService`,
  `GlobalBudgetSnapshotService`, `DefaultRetirementUseCase`, plus `SettingsModelAssembler` qui l'implémente) ; `SettingsModel`
  apparaît dans 26 fichiers de production d'`application` et `server`. Ils portent le bloc `settings` des réponses REST.
- `transition-snapshot` contient : `BudgetDataModel`, `SettingsModel`, `BudgetReader`, `SettingsReader`,
  `UnknownTresorerieFieldException`.

## 4. Reliquats de `server`

| Catégorie | Classes |
|---|---|
| Câblage (composition root) | `ServerApplication`, `DesktopLauncher`, `DomainEngineConfig`, `TransactionConfig`, `SiloMutationLockRegistry`, `SpringTransactionRunner`, `WebCorsConfig` |
| Planificateurs | `MarketDataRefreshScheduler`, `EnableBankingSyncScheduler` |
| Écoute transactionnelle | `NotificationBudgetMutationListener` |
| Stores JPA dont le port est dans `server` | `JpaLoanAdviceSettingsStore` (port `LoanAdviceSettingsStore`), `JpaObjectifsSettingsStore` (port `ObjectifsSettingsStore`) |
| Services web | cinq `*ServiceImpl`, `HeartbeatController`, `GlobalExceptionHandler`, `NotificationsMapper`, `TauxMarcheMapper`, `ApiErrorResponse` |
| Calcul resté dans `server` | `LoanAdviceSettingsService`, `LoanAdviceSettingsCodec`, `PlacementRateSuggestionInput`, `PlacementRateSuggestionService`, `PlacementRateSuggestionInputFactory` (ce dernier cite `BudgetDataModel` en commentaire seulement) |

## 5. Écarts constatés entre le document 21 et le code

Ces écarts expliquent en partie la perte de repères : le document 21 n'est plus une source fiable de **statut**.

1. **SILO-190** est marqué « Démarré » alors que son texte indique la porte franchie (CI verte, case cochée dans `14`).
2. **SILO-213** : la ligne de statut répète deux fois « Démarré » (`[ ] Démarré / [ ] Démarré`) ; le texte de la section 11
   (« généralisation à suivre par SILO-213 lot B ») est périmé : Banque est livrée (lots A, B et B2).
3. **SILO-240 lot B2** est décrit « en attente d'arbitrage », alors que la décision sur les trous d'intégrité figure dans la
   même section (« Décision retenue ») : il reste à l'implémenter, pas à l'arbitrer.
4. **SILO-206** : le lot A est livré ; le lot B (retrait de `GlobalCacheLockRelay` et de `BudgetCacheStore.mutationLock`) est
   décrit comme dépendant d'un « arbitrage » déjà rendu le 5 octobre 2026.
5. **SILO-230, SILO-250, SILO-290** : la case cochée est « Non commencé » ; la lecture est contre-intuitive (case cochée = état),
   mais correcte.
6. **SILO-210 et SILO-211 lot B** : décrits comme bloqués par SILO-206 et SILO-220 ; la décision D9 (6 octobre 2026) a levé
   cette dépendance mais le texte « Reste pour SILO-210 (lot B) » n'a pas été réécrit.
7. **SILO-100** : « livré » pour les lots A et B, alors que `SettingsReader` et `SettingsModelAssembler` subsistent (le document
   renvoie leur retrait à SILO-120 « solde » et SILO-220, jamais repris dans un patch nommé).
8. **SILO-120** : le texte constate que des fixtures de tests construisent encore un `BudgetDataModel` ; ces tests ne sont rattachés
   à aucun patch précis (« supprimées avec SILO-230 »).

## 6. Ce que ce relevé ne couvre pas

- Aucun test n'a été exécuté ; les mentions « tests à confirmer en CI » du document 21 (SILO-212 B2, SILO-213 B et B2) restent
  à vérifier sur la CI.
- Le contenu fonctionnel des `*ServiceImpl` hors Retraite n'a pas été relu : la liste des cas d'usage à créer (SILO-310 à
  SILO-319) se déduit du nombre de services, pas d'une analyse de leurs dépendances.
- Les services qui lisent un fragment hors verrou avant de le réécrire en entier (défaut constaté pour Banque, décision de
  SILO-213 lot B2) n'ont pas été recensés pour Retraite, Fiscalité, Crédit, Patrimoine et Trésorerie.
