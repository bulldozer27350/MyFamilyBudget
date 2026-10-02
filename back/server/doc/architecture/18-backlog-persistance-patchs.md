# 18 — Backlog de patchs pour la séparation de la persistance

Statut : 🟡 à valider

## Lecture obligatoire avant tout patch

0. Le palier historique `RF-B00` / `RF-B01` de `15-backlog-patchs.md` doit être mergé et vert ;
1. [`00-principes.md`](00-principes.md) — frontières et dépendances interdites ;
2. [`01-sequencement.md`](01-sequencement.md) — ordre global ;
3. [`13-persistance.md`](13-persistance.md) — cible, contraintes et paliers ;
4. [`16-tests.md`](16-tests.md) — garanties attendues avant Maven ;
5. le document du domaine concerné (`02` à `12`).

## Règles de livraison

Pour maximiser la parallélisation, les agents travaillent de préférence sur `feature/DB-xxx` et ne prennent pas de
dépendance implicite vers un patch non listé dans la colonne « Prérequis ».

- Un patch = un item de cette liste.
- Chaque patch doit compiler et laisser les tests obligatoires verts.
- Aucun patch JPA n'est autorisé à supprimer une relation du hub tant que le remplacement n'est pas déjà branché et
  testé.
- Les patchs « additifs » des domaines distincts peuvent avancer en parallèle.
- Les patchs qui modifient `BudgetDataEntity` doivent être courts et traités comme une file de migration explicite,
  car ce fichier est une zone de conflit commune.
- Un patch de persistance ne doit pas embarquer un refactoring métier non nécessaire à son propre objectif.
- Les changements JPA significatifs doivent être testés contre PostgreSQL.
- Ne pas transformer `BankImportEntity.jsonData` en modèle relationnel par principe : le format JSON peut rester une
  décision interne à Banque tant qu'il ne bloque pas la frontière.

## État de départ assumé

Le palier de ports de lecture du backlog historique est considéré comme réalisé :

- `BudgetReader`, `PatrimoineReader`, `RetirementReader`, `TaxReader`, `BankReader`, `LoanReader`, `GoalReader`,
  `SettingsReader` existent ;
- les adapters de persistance existent ;
- le branchement des lecteurs des services d'application est réalisé ;
- `PersistenceManager` reste néanmoins une implémentation de transition ;
- les command services existent, mais plusieurs ne sont encore que des wrappers autour de `PersistenceManager`.

Les patchs ci-dessous sont donc des **travaux de consolidation et de retrait du couplage**, pas une création
from-scratch de cette infrastructure.

## Vue d'ensemble

| ID | Titre | Prérequis | Parallélisable avec |
|---|---|---|---|
| DB-000 | Inventaire des owners et des zones de conflit | RF-B01 acquis | DB-010 |
| DB-010 | Renforcer les tests round-trip des adapters | DB-000 | DB-020, DB-030, DB-040 |
| DB-011 | Test PostgreSQL + redémarrage Spring | DB-010 | DB-020, DB-030, DB-040 |
| DB-020 | Finaliser les commands Retraite | DB-000 | DB-021, DB-030, DB-040 |
| DB-021 | Finaliser les commands Fiscalité | DB-000 | DB-020, DB-030, DB-040 |
| DB-030 | Finaliser les commands Patrimoine | DB-000 | DB-020, DB-021, DB-031 |
| DB-031 | Finaliser les commands Trésorerie | DB-000 | DB-020, DB-021, DB-030 |
| DB-040 | Finaliser les commands Banque | DB-000 | DB-020, DB-021, DB-030, DB-041 |
| DB-041 | Finaliser les commands Crédit/Objectifs | DB-000 | DB-020, DB-021, DB-030, DB-040 |
| DB-050 | Retirer les dernières mutations génériques | DB-020, DB-021, DB-030, DB-031, DB-040, DB-041 | DB-060 |
| DB-060 | Réduire `PersistenceManager` | DB-050 | DB-061 |
| DB-061 | Finaliser l'orchestrateur Settings sans owner global | DB-060 | JPA additif |
| DB-1000 | JPA Retraite — entités/repositories additifs | DB-060 | DB-1010, DB-1020, DB-1030, DB-1040 |
| DB-1001 | JPA Retraite — basculer l'adapter | DB-1000 | DB-1011, DB-1021, DB-1031, DB-1041 |
| DB-1010 | JPA Fiscalité — entités/repositories additifs | DB-060 | DB-1000, DB-1020, DB-1030, DB-1040 |
| DB-1011 | JPA Fiscalité — basculer l'adapter | DB-1010 | DB-1001, DB-1021, DB-1031, DB-1041 |
| DB-1020 | JPA Objectifs — entités/repositories additifs | DB-060 | DB-1000, DB-1010, DB-1030, DB-1040 |
| DB-1021 | JPA Objectifs — basculer l'adapter | DB-1020 | DB-1001, DB-1011, DB-1031, DB-1041 |
| DB-1030 | JPA Banque — entités/repositories additifs | DB-060 | DB-1000, DB-1010, DB-1020, DB-1040 |
| DB-1031 | JPA Banque — basculer l'adapter | DB-1030 | DB-1001, DB-1011, DB-1021, DB-1041 |
| DB-1040 | JPA Crédit — entités/repositories additifs | DB-060 | DB-1000, DB-1010, DB-1020, DB-1030 |
| DB-1041 | JPA Crédit — basculer l'adapter | DB-1040 | DB-1001, DB-1011, DB-1021, DB-1031 |
| DB-1050 | JPA Patrimoine — entités/repositories additifs | DB-060 | DB-1000, DB-1010, DB-1020, DB-1030, DB-1040 |
| DB-1051 | JPA Patrimoine — basculer l'adapter | DB-1050 | DB-1001, DB-1011, DB-1021, DB-1031, DB-1041 |
| DB-1060 | JPA Trésorerie — entités/repositories additifs | DB-060 | DB-1050, DB-1000, DB-1010, DB-1030, DB-1040 |
| DB-1061 | JPA Trésorerie — basculer l'adapter | DB-1060 | DB-1051 |
| DB-1070 | Vérifier les readers après bascule JPA | DB-1001, DB-1011, DB-1021, DB-1031, DB-1041 | DB-1051 |
| DB-1080 | Vérifier les parcours E2E après bascule JPA | DB-1051, DB-1061, VT-220, VT-320 | DB-1070 |
| DB-1100 | Retirer la relation hub Retraite de `BudgetDataEntity` | DB-1070 | aucun autre hub-cleanup |
| DB-1110 | Retirer la relation hub Fiscalité | DB-1100 | — |
| DB-1120 | Retirer la relation hub Objectifs | DB-1110 | — |
| DB-1130 | Retirer la relation hub Banque | DB-1120 | — |
| DB-1140 | Retirer la relation hub Crédit | DB-1130 | — |
| DB-1150 | Retirer la relation hub Patrimoine | DB-1140 | — |
| DB-1160 | Retirer la relation hub Trésorerie | DB-1150 | — |
| DB-1170 | Nettoyer `EntityModelConverter` en mappers par domaine | DB-1160 | DB-1180 |
| DB-1180 | Réduire `BudgetDataModel` au snapshot global | DB-1170, VT-500 | DB-1190 |
| DB-1190 | Nettoyage final du bootstrap/persistence legacy | DB-1180 | DB-1200 |
| DB-1200 | Gate persistance avant Maven | DB-011, DB-061, DB-1080, DB-1190, VT-320, VT-330, VT-340, VT-350 | aucun |

---

## DB-000 — Inventaire des owners et des zones de conflit

- **Prérequis** : RF-B01 acquis.
- **Objectif** : produire une carte exploitable avant les premiers patchs JPA.
- **Travaux** : pour chaque modèle, indiquer owner, entité actuelle, repository, adapter, callers, relations vers
  `BudgetDataEntity`, tests existants et test E2E de preuve.
- **Livrable** : aucune modification de production ; tableau dans ce document ou note liée.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [x] Terminé

### Inventaire (état du code au moment de DB-000)

Chemins relatifs à `back/server/src/main/java/com/moe/myfamilybudget/server/internal/`. Les « repositories » sont
dans `persistence/repository/`, les « adapters » dans `persistence/adapter/`, les « commands » dans `command/`.

#### 1. Entités rattachées au hub `BudgetDataEntity`

`BudgetDataEntity` (singleton, `BudgetDataRepository.findFirstByOrderByIdAsc`) porte aujourd'hui toutes les
relations ci-dessous, avec `cascade = ALL` (et `orphanRemoval` pour `retirement` et `bankImport`), en `EAGER`
sauf `bankImport` (`LAZY`).

| Owner cible | Champ du hub | Entité | Repository dédié | Reader / adapter actuel | Command actuelle |
|---|---|---|---|---|---|
| Budget de base | `incomes` | `IncomeEntity` | `IncomeRepository` | `BudgetReader` / `BudgetPersistenceAdapter` | aucune (via Trésorerie) |
| Budget de base | `charges` | `ChargeEntity` | `ChargeRepository` | idem | aucune (via Trésorerie) |
| Budget de base | `oneoff` | `OneOffExpenseEntity` | `OneOffExpenseRepository` | idem | aucune (via Trésorerie) |
| Budget de base | `transfers` | `TransferEntity` | `TransferRepository` | idem | aucune (via Trésorerie) |
| Budget de base | `variableIncomes` | `VariableIncomeEntity` | `VariableIncomeRepository` | idem | aucune (via Trésorerie) |
| Budget de base | `variableOverrides` | `VariableOverrideEntity` | `VariableOverrideRepository` | idem | aucune (via Trésorerie) |
| Retraite | `retirement` | `RetirementEntity` | `RetirementRepository` | `RetirementReader` / `RetirementPersistenceAdapter` | `RetirementCommandService` |
| Fiscalité | `taxChildren` | `TaxChildEntity` | `TaxChildRepository` | `TaxReader` / `TaxPersistenceAdapter` | `TaxCommandService` |
| Fiscalité | `taxBrackets` | `TaxBracketEntity` | `TaxBracketRepository` | idem | idem |
| Fiscalité | `taxRateOverrides` | `TaxRateOverrideEntity` | `TaxRateOverrideRepository` | idem | idem |
| Fiscalité | `taxActualOverrides` | `TaxActualOverrideEntity` | `TaxActualOverrideRepository` | idem | idem |
| Patrimoine | `placements` | `PlacementEntity` | `PlacementRepository` | `PatrimoineReader` / `PatrimoinePersistenceAdapter` | `PatrimoineCommandService` |
| Patrimoine | `realEstate` | `RealEstateEntity` | `RealEstateRepository` | idem | idem |
| Patrimoine | `assetCategories` | `AssetCategoryEntity` | `AssetCategoryRepository` | idem | idem |
| Banque | `bankImport` | `BankImportEntity` (`jsonData`) | `BankImportRepository` | `BankReader` / `BankPersistenceAdapter` | `BankImportCommandService` |
| Crédit | `loans` | `LoanEntity` | `LoanRepository` | `LoanReader` / `LoanPersistenceAdapter` | `LoanCommandService` (délègue à `PatrimoineCommandService`) |
| Objectifs | `objectifs` | `ObjectifEntity` | `ObjectifRepository` | `GoalReader` / `GoalPersistenceAdapter` | `GoalCommandService` (délègue à `PatrimoineCommandService`) |
| Settings | `settings` (`@OneToOne`, côté hub) | `SettingsEntity` | `SettingsRepository` | `SettingsReader` / `SettingsPersistenceAdapter` | pas de command ; `ParametersServiceImpl.saveSettings` (`@Transactional`) |

Notes :

- Aucune entité Trésorerie dédiée : le domaine lit/écrit les lignes du budget de base ;
  `TresorerieCommandService` appelle `addTresorerieRow`, `updateTresorerieRow`, `removeTresorerieRow` et
  `applyTresorerieAjustement` de `PersistenceManager` (contrats `listKey` / `field` / `value`).
- `ObjectifEntity` et `LoanEntity` n'ont pas encore de command propriétaire : leurs mutations passent par
  `PatrimoineCommandService` (`GoalCommandService` et `LoanCommandService` n'ont aucune dépendance directe à
  `PersistenceManager`).

#### 2. Entités enfants (sans lien direct avec le hub)

| Entité | Parent | Owner cible |
|---|---|---|
| `RetirementPersonEntity` | `RetirementEntity` | Retraite |
| `SalaryHistoryEntity` | `RetirementPersonEntity` | Retraite |
| `PlacementHistoryEntryEntity` | `PlacementEntity` | Patrimoine |
| `ObjectifAllocationEntity` | `ObjectifEntity` | Objectifs |

#### 3. Entités autonomes (hors hub) déjà isolées

| Entité | Repository | Utilisé par (hors package `persistence`) |
|---|---|---|
| `ObjectifsSettingsEntity` | `ObjectifsSettingsRepository` | `calculation/JpaObjectifsSettingsStore` |
| `LoanAdviceSettingsEntity` | `LoanAdviceSettingsRepository` | `calculation/JpaLoanAdviceSettingsStore` |
| `MarketSnapshotEntity` | `MarketSnapshotRepository` | `marketdata/JpaMarketSnapshotStore` |
| `NotificationSettingsEntity` | `NotificationSettingsRepository` | `notification/JpaNotificationSettingsStore`, `notification/NotificationDispatchService`, `impl/NotificationsServiceImpl` |
| `NotificationSentLogEntity` | `NotificationSentLogRepository` | `notification/NotificationDispatchService` |
| `PushSubscriptionEntity` | `PushSubscriptionRepository` | `notification/channel/WebPushNotificationChannel`, `impl/NotificationsServiceImpl` |
| `EnableBankingSyncStateEntity` | `EnableBankingSyncStateRepository` | `enablebanking/EnableBankingSyncService` |

Ces sept entités n'ont **aucune relation vers le hub** : elles ne sont pas concernées par `DB-1100` à `DB-1160`.

#### 4. Callers de `PersistenceManager` hors package `persistence`

| Classe | Méthodes appelées |
|---|---|
| `command/RetirementCommandService` | `updateRetirement` |
| `command/TaxCommandService` | `updateTaxConfig`, `updateTaxSettings`, `resetDefaultTaxBrackets`, `lockForCurrentTransaction` |
| `command/PatrimoineCommandService` | `savePatrimoineRow`, `deletePatrimoineRow`, `addAssetCategory`, `updateAssetCategory`, `removeAssetCategory`, `addPlacementHistoryEntry`, `updatePlacementHistoryEntry`, `deletePlacementHistoryEntry` |
| `command/TresorerieCommandService` | `addTresorerieRow`, `updateTresorerieRow`, `removeTresorerieRow`, `applyTresorerieAjustement` |
| `command/BankImportCommandService` | `updateBankImport` |
| `enablebanking/EnableBankingSyncService` | `getBankImport` |
| `impl/SystemeServiceImpl` | `getBudgetData`, `setBudgetData`, `resetData`, `lockForCurrentTransaction` |
| `notification/NotificationDispatchService` | `getBudgetData` |

Les autres services de `impl/` (`AnalyseServiceImpl`, `AnalysePretsServiceImpl`, `ImpotsServiceImpl`,
`OverviewServiceImpl`, `ParametersServiceImpl`, `PatrimoineServiceImpl`, `PendingOperationsServiceImpl`,
`PointageServiceImpl`, `RetraiteServiceImpl`, `StatementBankImportServiceImpl`, `SuggestionsTauxServiceImpl`,
`TresorerieServiceImpl`) référencent encore `PersistenceManager` dans leurs imports ou leurs constructeurs, mais
aucun appel direct à une de ses méthodes n'a été relevé en dehors de ceux listés ci-dessus : cela devra être
confirmé par DB-050 / DB-1070 (recherche statique).

Les 8 adapters (`persistence/adapter/*PersistenceAdapter`) dépendent tous de `PersistenceManager` et lisent via
`getBudgetData()` : ils sont le principal point de bascule des phases B de `DB-1001` à `DB-1061`.

#### 5. Zones de conflit communes

| Fichier | Lignes (approx.) | Raison | Patchs concernés |
|---|---|---|---|
| `persistence/entity/BudgetDataEntity.java` | 70 | hub : toutes les relations | `DB-1100` à `DB-1160` (sérialisés) |
| `persistence/converter/EntityModelConverter.java` | 695 | conversion globale modèle ↔ entités | `DB-1000`…`DB-1061` (additifs : ne pas le modifier), `DB-1170` |
| `persistence/BudgetMutationService.java` | 998 | mutations génériques et verrou transactionnel | `DB-020`…`DB-050`, `DB-060` |
| `persistence/PersistenceManager.java` | 342 | façade de transition | `DB-020`…`DB-060`, `DB-1190` |
| `persistence/BudgetPersistenceGateway.java` | 361 | accès repositories globaux | `DB-060`, `DB-1190` |
| `persistence/BudgetCacheStore.java` | 384 | cache mémoire + rollback | `DB-060`, `DB-1180` |
| `model/BudgetDataModel.java` | 264 | snapshot global consommé par les adapters | `DB-1180` |
| `updater/*FieldUpdaters.java`, `FieldValueConverter`, `TresorerieFieldUpdateDispatcher` | — | contrats `listKey` / `field` / `value` | `DB-031`, `DB-050` |

Recommandation de découpage : les patchs `DB-1000`…`DB-1061` doivent créer de **nouveaux** fichiers et ne pas toucher
à `EntityModelConverter` ni à `BudgetDataEntity` (règle de la phase A).

#### 6. Tests existants et preuve E2E

Chemins relatifs à `back/server/src/test/java/com/moe/myfamilybudget/server/internal/`.

| Domaine / sujet | Tests existants |
|---|---|
| Adapters (tous domaines) | `persistence/adapter/PersistenceAdaptersTest` |
| Cache, rollback, concurrence | `persistence/BudgetCacheStoreRollbackTest`, `persistence/BudgetCacheStoreConcurrencyTest`, `persistence/WriteFailureKeepsMemoryTest` |
| Mapping Crédit / taux | `persistence/LoanContractInfoConversionTest`, `persistence/RatePrecisionPersistenceTest` |
| Redémarrage Spring | `integration/RestartPersistenceTest` (VT-320) |
| Atomicité multi-domaines | `integration/MultiDomainAtomicityTest` |
| Concurrence API | `integration/ConcurrentMutationsApiTest` |
| Caractérisation REST | `integration/CriticalEndpointsCharacterizationTest` |
| Parcours Retraite → Overview | `integration/RetirementToOverviewScenarioTest` |
| Banque / Pointage / Analyse | `integration/BankPointageAnalyseScenarioTest` |
| Logique métier globale | `integration/BusinessLogicIntegrationTest` |
| Garde-fous d'architecture | `architecture/CalculationDependenciesArchTest` |
| Fabrique de test | `testsupport/PersistenceManagerTestFactory` |

Preuves E2E Playwright de référence : voir `16-tests.md` (`VT-220` Patrimoine → Trésorerie → Overview, `VT-230`
paramètres multi-domaines, `VT-240` backend indisponible, `VT-500` suite finale F1/F3/F4).

Lacunes constatées pour les patchs suivants :

- aucun test dédié par adapter pour Fiscalité, Banque, Crédit et Objectifs au-delà de `PersistenceAdaptersTest`
  (à traiter par `DB-010`) ;
- aucune exécution PostgreSQL dédiée aux adapters : `RestartPersistenceTest` est la seule preuve de redémarrage
  (à traiter par `DB-011`).

## DB-010 — Renforcer les tests round-trip des adapters

- **Prérequis** : DB-000.
- **Objectif** : remplacer les vérifications « non-null » par des preuves write/read.
- **Travaux** : tester les objets critiques, le reset et les cas optionnels ; conserver H2 pour la boucle rapide.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [x] Terminé
- **Livré** : `PersistenceAdaptersJpaRoundTripTest` (H2, vrais repositories) : écriture par le `PersistenceManager` du
  contexte, relecture par un second `PersistenceManager` à cache vierge ; couvre import complet, second import,
  mutations ciblées (Retraite, Fiscalité, catégories d'actifs, Banque), reset et cas optionnels. `PersistenceAdaptersTest`
  (VT-310, repositories mockés) est conservé.

## DB-011 — Test PostgreSQL + redémarrage Spring

- **Prérequis** : DB-010.
- **Objectif** : prouver que les mutations critiques survivent à un nouveau contexte Spring.
- **Travaux** : mutation → GET → redémarrage → GET ; au moins une exécution PostgreSQL.
- **Relation** : doit alimenter `VT-320` plutôt que dupliquer une suite indépendante.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [x] Terminé
- **Livré** : pas de nouvelle suite : le scénario de `RestartPersistenceTest` (VT-320) est étendu aux écritures des
  command services finalisés en DB-020 à DB-041 — Retraite (`PUT /retraite`), Fiscalité (`PUT /impots`), Banque
  (`PUT /bank-import/transactions/{id}/category`), Crédit (`POST /patrimoine/loans`), Objectifs
  (`POST /patrimoine/objectifs`) et historique de placement — avec mêmes assertions avant et après redémarrage. Les
  deux variantes (H2 fichier, toujours exécutée ; PostgreSQL, exécutée en CI par le service `postgres` et exigée par
  le gate VT-600) en bénéficient. Aucun changement de CI ni de code de production.
- **Exécution locale** : `mvn -Dtest=RestartPersistenceTest test` (H2) ; avec PostgreSQL, définir
  `MFB_TEST_POSTGRES_URL` (voir `17-backlog-tests-patchs.md`, VT-320).

## DB-020 à DB-041 — Commands orientées owner

### Statut DB-040 — Finaliser les commands Banque

- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [x] Terminé
- **Livré** : port `BankWriter` (`updateBankImport`) implémenté par `BankPersistenceAdapter` (qui implémente aussi
  `BankReader`) ; `BankImportCommandService` ne dépend plus de `PersistenceManager`, refuse un import `null`
  (`IllegalArgumentException`, 400) et laisse l'erreur de persistance remonter telle quelle.
  `EnableBankingSyncService` lit désormais via `BankReader` au lieu de `PersistenceManager` (dernier appel métier
  Banque hors command/reader). `BankImportEntity.jsonData` reste interne à Banque ; contrats REST inchangés.
- **Tests** : `BankImportCommandServiceTest` (délégation, validation, propagation, relecture via
  `BankPersistenceAdapter`) ; 6 tests d'`impl/` adaptés au nouveau constructeur.

Pour chaque domaine :

- remplacer les derniers appels métier à `PersistenceManager` par le command service propriétaire ;
- conserver les contrats REST ;
- tester validation, succès et propagation de l'erreur ;
- supprimer les méthodes de `PersistenceManager` uniquement quand plus aucun appelant ne les utilise.

Les domaines sont volontairement séparés afin que plusieurs agents puissent réaliser DB-020, DB-021, DB-030,
DB-031, DB-040 et DB-041 en parallèle.

### Statut DB-031 — Finaliser les commands Trésorerie

- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [x] Terminé
- **Livré** : port `TresorerieWriter` (4 opérations : ajout, mise à jour, suppression, ajustement) implémenté par
  `TresoreriePersistenceAdapter` ; `TresorerieCommandService` ne dépend plus de `PersistenceManager`, valide les
  identifiants (`listKey`, `id`, `field`, `lineId`, `kind`, `newMonthly` : `IllegalArgumentException`, traduite en
  400 par `GlobalExceptionHandler`) et laisse l'erreur de persistance remonter telle quelle. Corps `null` (ajout)
  et valeur `null` (mise à jour) restent acceptés ; contrats REST et `listKey` / `field` / `value` inchangés
  (retrait en DB-050).
- **Tests** : `TresorerieCommandServiceTest` (délégation, validation, propagation, relecture via
  `BudgetPersistenceAdapter`) ; `TresorerieServiceImplTest` adapté au nouveau constructeur.

### Statut DB-041 — Finaliser les commands Crédit/Objectifs

- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [x] Terminé
- **Livré** : ports `LoanWriter` et `GoalWriter` implémentés par `LoanPersistenceAdapter` et
  `GoalPersistenceAdapter` (qui fixent la liste `loans` / `objectifs` côté adapter). `LoanCommandService` et
  `GoalCommandService` ne délèguent plus à `PatrimoineCommandService` et valident l'identifiant de suppression
  (`IllegalArgumentException`, 400) ; corps `null` accepté à la sauvegarde. `PatrimoineServiceImpl` route désormais
  `loans` / `credits` (insensible à la casse) vers le command Crédit et `objectifs` vers le command Objectifs ; les
  autres `listKey` restent sur `PatrimoineCommandService`. Contrats REST inchangés (retrait du `listKey` en DB-050).
- **Tests** : `LoanCommandServiceTest`, `GoalCommandServiceTest` (délégation, validation, propagation, relecture via
  l'adapter) ; `PatrimoineServiceImplTest` : routage prêts (+ alias `credits`) et objectifs, constructeur adapté.

## DB-050 — Retirer les dernières mutations génériques

- **Prérequis** : DB-020, DB-021, DB-030, DB-031, DB-040, DB-041.
- **Objectif** : faire disparaître les contrats `listKey` / `field` / `value` des chemins métier ordinaires.
- **Validation** : suite backend + Playwright des écrans concernés.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [x] Terminé
- **Livré (lot 1 — `listKey` et `kind`)** : enums `TresorerieList`, `TresorerieAdjustmentKind` et
  `PatrimoineList` (package `port`), interprétés **une seule fois** à la frontière REST
  (`TresorerieServiceImpl`, `PatrimoineServiceImpl`) ; `TresorerieCommandService`, `PatrimoineCommandService`,
  `TresorerieWriter`, `PatrimoineWriter` et leurs adapters ne manipulent plus de `listKey` / `kind` en chaîne. Seuls
  les adapters traduisent l'enum vers la clé historique (`list.key()`) pour appeler `PersistenceManager`, qui reste
  inchangé (réduction en DB-060). Contrats REST inchangés ; une `listKey` inconnue est désormais refusée en 400
  pour l'ajout, la suppression et la sauvegarde (avant : no-op silencieux ; la mise à jour la refusait déjà).
- **Livré (lot 2 — `field` fiscalité et catégories d'actifs)** : enums `TaxSettingField` et `AssetCategoryField`
  (package `port`) ; `TaxCommandService.updateTaxSettings`, `PatrimoineCommandService.updateAssetCategory`, leurs ports
  et adapters sont typés. L'interprétation du nom de champ se fait à la frontière REST (`ImpotsServiceImpl`,
  `ParametersServiceImpl`) via `find(...)` : un champ inconnu reste sans effet, comme avant (aucun nouveau 400).
- **Livré (lot 3 — `field` Trésorerie)** : enum `TresorerieLineField` (union des 29 champs des `*FieldUpdaters`) ;
  `TresorerieCommandService.updateTresorerieRow`, `TresorerieWriter` et l'adapter sont typés. Le champ est interprété
  à la frontière REST (`TresorerieServiceImpl`) : un champ inconnu est refusé en 400 (`UnknownTresorerieFieldException`,
  comme avant quand la ligne existait ; désormais aussi si la ligne n'existe pas). La validité d'un champ pour une liste
  reste vérifiée par les `*FieldUpdaters`.
- **Reporté volontairement** : les chaînes `listKey` / `field` qui subsistent sont confinées (a) à la frontière REST
  (Banque : `StatementBankImportServiceImpl`, dont la command est déjà typée) et (b) à `PersistenceManager` /
  `BudgetMutationService` / `*FieldUpdaters`, appelés uniquement par les adapters (`list.key()`, `field.key()`,
  `LIST_KEY` de `LoanPersistenceAdapter` / `GoalPersistenceAdapter`). Leur retrait relève de DB-060 (réduction de
  `PersistenceManager`) et de DB-1190, car les adapters en dépendent jusqu'à la bascule JPA.
- **Tests** : `TresorerieCommandServiceTest`, `PatrimoineCommandServiceTest` et `TaxCommandServiceTest` adaptés aux
  enums (+ `fromKey` / `fromKind` / `find`, valeur inconnue ou `null`) ; `TresorerieServiceImplTest` : champ ou liste
  inconnus refusés.

## DB-060 — Réduire `PersistenceManager`

- **Prérequis** : DB-050.
- **Objectif** : conserver seulement les responsabilités réellement transverses.
- **Travaux** : import/export/reset global, bootstrap ou orchestration qui ne possède pas de owner unique ; retirer
  les opérations de domaine déjà transférées.
- **Critère** : aucun calculateur métier n'a besoin de cette classe.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [x] Terminé
- **Livré** : les 17 méthodes de mutation de domaine (Trésorerie, Patrimoine, Retraite, Fiscalité, catégories d'actifs,
  historique de placement, Banque) quittent `PersistenceManager` pour `DomainMutations` (même package, constructeur
  non public, pas un bean Spring). `PersistenceManager` ne garde que : `init`, `getBudgetData`, `setBudgetData`,
  `resetData`, `lockForCurrentTransaction`, `getBankImport` (lecture du snapshot) et le point d'entrée
  transactionnel `write(Consumer)` / `writeAndGet(Function)`, qui donne accès à `DomainMutations` à l'intérieur du
  `@Transactional` de classe (transaction, verrou VT-350b et événement `BudgetMutatedEvent` inchangés).
  Les adapters (`*PersistenceAdapter`) appellent `persistenceManager.write(m -> m.xxx(...))` ; leurs constructeurs
  et ceux des tests restent inchangés. Aucun appelant hors adapters et tests : services, commands et calculateurs
  n'utilisent plus que `getBudgetData` / `setBudgetData` / `resetData` / verrou (`SystemeServiceImpl`,
  `NotificationDispatchService`).
- **Reste (hors DB-060)** : `getBankImport` et `DomainMutations` disparaissent avec la bascule JPA par domaine
  (DB-1001…DB-1061) et DB-1190.
- **Tests** : `WriteFailureKeepsMemoryTest`, `PersistenceAdaptersTest`, `PersistenceAdaptersJpaRoundTripTest`,
  `StatementBankImportServiceImpl(Integration)Test` migrés vers `write(...)` sans changement d'assertion.

## DB-061 — Finaliser Settings sans owner global

- **Prérequis** : DB-060.
- **Objectif** : finir la distribution des champs Settings vers les owners et conserver la façade REST composite.
- **Travaux** : Retirement/Fiscality/Treasury/Goals/Simulation/EconomicAssumptions ; transaction locale unique si
  nécessaire.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

---

## Méthode commune pour DB-1000…DB-1061

Chaque domaine suit deux patchs avant toute suppression de relation dans le hub.

### Phase A — additive

Créer les entités/repositories propres au domaine **sans supprimer ni modifier le chemin legacy**. Cette contrainte
est volontaire : elle rend les branches de domaines distincts presque totalement indépendantes.

### Phase B — bascule

Faire utiliser le nouvel adapter JPA au use case concerné, ajouter les tests round-trip + restart et conserver
encore l'ancien mapping pour permettre un rollback simple.

### Phase C — suppression du hub

Elle est traitée plus tard, une par une (`DB-1100` à `DB-1160`). Ainsi les patchs coûteux et conflictuels ne bloquent
pas les agents qui travaillent sur les autres domaines.

## DB-1000 — JPA Retraite — entités/repositories additifs

- **Prérequis** : DB-060.
- **Périmètre** : entités et repositories Retraite uniquement.
- **Objectif** : créer la cible JPA autonome sans changer encore le comportement.
- **Validation** : compilation + tests mapping/round-trip.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [x] Terminé
- **Livré** : entités `PensionPlanEntity` (table `pension_plan`, hypothèses PASS / point Agirc, singleton fonctionnel),
  `PensionPersonEntity` (`pension_person`, FK `plan_id`, `position`) et `PensionSalaryEntity` (`pension_salary`, FK
  `person_id`, `position`), `PensionPlanRepository` (`findFirstByOrderByIdAsc`) et `PensionEntityMapper`
  (`RetirementModel` ↔ `PensionPlanEntity`, sans perte, aucun défaut `getEffective*` appliqué). Taux, valeur de
  point, ratios et points Agirc en `NUMERIC(19,8)`, PASS et salaires en `NUMERIC(19,2)`. Collections `EAGER` en
  `FetchMode.SELECT` (deux listes chargées par jointure provoqueraient une `MultipleBagFetchException`). Aucune
  relation vers `BudgetDataEntity` ; `RetirementEntity`, `RetirementPersonEntity`, `SalaryHistoryEntity`,
  `EntityModelConverter`, `RetirementPersistenceAdapter` et le hub sont **inchangés** (chemin legacy intact, aucune
  donnée migrée). Les nouvelles tables sont créées vides par `ddl-auto` et restent inutilisées jusqu'à DB-1001.
  Le préfixe `Pension*` évite la collision avec les classes legacy `Retirement*`.
- **Tests** : `PensionJpaModelTest` (H2, `@DataJpaTest`) : aller-retour multi-personnes avec ordre des personnes et
  des salaires conservé, taux non arrondis, champs optionnels à `null`, listes absentes relues vides, suppression
  des salaires orphelins à la mise à jour, cascade à la suppression, tolérance du mapper à `null`.
- **Pour DB-1001** : reproduire le schéma de DB-1021 / DB-1041 (lecture JPA, recopie par `BudgetPersistenceGateway`
  dans la transaction de sauvegarde, reconstruction depuis le hub au chargement du cache) ; vider la table avec
  `deleteAll` + `flush` avant réinsertion.

## DB-1001 — JPA Retraite — basculer l'adapter

- **Prérequis** : DB-1000.
- **Objectif** : faire passer le reader/command Retraite sur les nouveaux repositories.
- **Validation** : `VT-320` adapté au domaine + tests E2E Retraite si disponibles.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

## DB-1010 / DB-1011 — JPA Fiscalité

- **DB-1010** : entités/repositories additifs, sans toucher au hub.
- **DB-1011** : bascule de l'adapter, tests round-trip + restart.
- **Parallèle** : toute la phase additive/bascule peut être développée en parallèle de Retraite et Objectifs.

### Statut DB-1010 — JPA Fiscalité — entités/repositories additifs

- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [x] Terminé
- **Livré** : entités `FiscalChildEntity` (`fiscal_child`), `FiscalBracketEntity` (`fiscal_bracket`, taux
  `NUMERIC(19,8)`), `FiscalRateOverrideEntity` (`fiscal_rate_override`) et `FiscalActualOverrideEntity`
  (`fiscal_actual_override`), leurs quatre repositories (`findAllByOrderByPositionAsc`) et `FiscalEntityMapper`
  (`Tax*Model` ↔ `Fiscal*Entity`, sans perte). Clé technique générée et colonne `position` : aucune hypothèse
  d'unicité sur `uid` ou sur l'année (comme dans le chemin legacy), donc aucun risque de rejet à l'import.
  Aucune relation vers `BudgetDataEntity` ; `Tax*Entity`, `EntityModelConverter`, `TaxPersistenceAdapter` et le hub
  sont **inchangés** (chemin legacy intact, aucune donnée migrée). Les nouvelles tables sont créées vides par
  `ddl-auto` et restent inutilisées jusqu'à DB-1011. Les paramètres fiscaux scalaires (`taxAbattement`, etc.)
  restent dans Settings (DB-061).
- **Tests** : `FiscalJpaModelTest` (H2, `@DataJpaTest`) : aller-retour des quatre listes avec ordre conservé,
  taux non arrondis, dernière tranche sans plafond, doublons d'année tolérés, remplacement du contenu
  (suppression + `flush` + réinsertion), tolérance à `null`.
- **Pour DB-1011** : reproduire le schéma de DB-1021 / DB-1041 (lecture JPA, recopie par `BudgetPersistenceGateway`
  dans la transaction de sauvegarde, reconstruction depuis le hub au chargement du cache). Le barème par défaut
  appliqué quand la liste importée est vide est porté par le chemin d'écriture existant ; la lecture JPA doit
  restituer ce que le cache contient.

### Statut DB-1011 — JPA Fiscalité — basculer l'adapter

- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [x] Terminé
- **Livré** : `TaxPersistenceAdapter` lit désormais `FiscalChildRepository`, `FiscalBracketRepository`,
  `FiscalRateOverrideRepository` et `FiscalActualOverrideRepository` (tables `fiscal_*`) via `FiscalEntityMapper`.
  Les écritures (`updateTaxConfig`, `updateTaxSettings`, `resetDefaultTaxBrackets`) restent portées par
  `PersistenceManager` ; `BudgetPersistenceGateway` recopie la fiscalité du modèle dans les tables autonomes à chaque
  sauvegarde, dans la même transaction (import, reset et mutations de tous domaines inclus : rollback cohérent). Au
  chargement du cache (démarrage), les tables sont reconstruites depuis le hub, ce qui migre les données existantes
  sans script. Le barème recopié est le barème **effectif** (barème par défaut si la liste du modèle est vide) : la
  lecture JPA restitue donc exactement ce que le cache expose. Les quatre repositories sont injectés dans
  `PersistenceManager` (constructeur élargi ; fabrique et tests adaptés). Les paramètres fiscaux scalaires restent
  dans Settings (DB-061).
- **Retour arrière** : le hub (`tax_*`) reste alimenté et reste la source de chargement du cache. Le constructeur
  `TaxPersistenceAdapter(PersistenceManager)` conserve la lecture depuis le cache (tests unitaires à repositories
  mockés) ; revenir au comportement antérieur en production consiste à revenir sur ce patch.
- **Tests** : `PersistenceAdaptersJpaRoundTripTest` (H2) : fiscalité d'un import relue par JPA, `updateTaxConfig` /
  `resetDefaultTaxBrackets` visibles via la lecture JPA, import sans barème (barème par défaut), reconstruction des
  tables au démarrage, reset. Constructeurs de `PersistenceManager` adaptés dans `PersistenceManagerTestFactory`,
  `WriteFailureKeepsMemoryTest` et le round-trip.
- **Reste** : `VT-320` (redémarrage, H2 + PostgreSQL) couvre déjà `PUT /impots` ; à exécuter en CI. La suppression
  de la relation du hub relève de DB-1110.

## DB-1020 / DB-1021 — JPA Objectifs

- **DB-1020** : modèle JPA additif.
- **DB-1021** : bascule adapter + tests.
- **Parallèle** : Retraite/Fiscalité/Banque/Crédit.

### Statut DB-1020 — JPA Objectifs — entités/repositories additifs

- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [x] Terminé
- **Livré** : entités `GoalEntity` (table `goal`, clé `id` = identifiant métier, `position`) et `GoalAllocationEntity`
  (table `goal_allocation`, clé `goal_id`, `position`), `GoalRepository` (`findAllByOrderByPositionAsc`) et
  `GoalEntityMapper` (`ObjectifModel` ↔ `GoalEntity`, sans perte, sans rejouer `LegacyObjectifAllocationMigrator`).
  Aucune relation vers `BudgetDataEntity` ; `ObjectifEntity`, `ObjectifAllocationEntity`, `EntityModelConverter`,
  `GoalPersistenceAdapter` et le hub sont **inchangés** (chemin legacy intact, aucune donnée migrée). Les nouvelles tables
  sont créées vides par `ddl-auto` et restent inutilisées jusqu'à DB-1021.
- **Tests** : `GoalJpaModelTest` (H2, `@DataJpaTest`) : aller-retour avec allocations multi-comptes et ordre conservé,
  champs historiques et optionnels, suppression des allocations orphelines à la mise à jour, cascade à la suppression,
  refus d'un objectif sans identifiant.
- **Pour DB-1021** : migrer les lignes `objectif` / `objectif_allocation` vers `goal` / `goal_allocation` et décider du
  sort du filet `LegacyObjectifAllocationMigrator`.

### Statut DB-1021 — JPA Objectifs — basculer l'adapter

- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [x] Terminé
- **Livré** : `GoalPersistenceAdapter.getGoals()` lit désormais `GoalRepository` (tables `goal` / `goal_allocation`) via
  `GoalEntityMapper`. Les écritures (`saveGoalRow`, `deleteGoalRow`) restent portées par `PersistenceManager`, car la
  validation des allocations dépend du cache (comptes et autres objectifs) ; `BudgetPersistenceGateway` recopie les
  objectifs du modèle dans les tables autonomes à chaque sauvegarde, dans la même transaction (import, reset et
  mutations de tous domaines inclus : rollback cohérent). Au chargement du cache (démarrage), les tables autonomes
  sont reconstruites depuis le hub, ce qui migre les données existantes sans script. `GoalRepository` est injecté
  dans `PersistenceManager` (constructeur élargi ; fabrique et tests adaptés).
- **Retour arrière** : le hub (`objectif` / `objectif_allocation`) reste alimenté et reste la source de chargement du
  cache ; `LegacyObjectifAllocationMigrator` continue de s'appliquer à ce chargement. Le constructeur
  `GoalPersistenceAdapter(PersistenceManager)` conserve la lecture depuis le cache (tests unitaires à repositories
  mockes) ; revenir au comportement antérieur en production consiste à revenir sur ce patch.
- **Tests** : `PersistenceAdaptersJpaRoundTripTest` (H2) : objectifs d'un import relus par JPA, création / mise à jour /
  suppression visibles via la lecture JPA, reconstruction des tables au démarrage, reset. Constructeurs de
  `PersistenceManager` adaptés dans `PersistenceManagerTestFactory`, `WriteFailureKeepsMemoryTest` et le round-trip.
- **Reste** : `VT-320` (redémarrage, H2 + PostgreSQL) couvre déjà `POST /patrimoine/objectifs` ; à exécuter en CI. La
  suppression de la relation du hub relève de DB-1120.

## DB-1030 / DB-1031 — JPA Banque

- **DB-1030** : entités/repositories Banque additifs ; `BankImportEntity.jsonData` peut rester interne.
- **DB-1031** : bascule des adapters Banque concernés, tests transaction/import/pointage.
- **Parallèle** : Retraite/Fiscalité/Objectifs/Crédit.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

## DB-1040 / DB-1041 — JPA Crédit

- **DB-1040** : entités/repositories des prêts additifs.
- **DB-1041** : bascule adapter + tests.
- **Parallèle** : tous les domaines hors modifications du hub.

### Statut DB-1040 — JPA Crédit — entités/repositories additifs

- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [x] Terminé
- **Livré** : entité `CreditLoanEntity` (table `credit_loan`, clé `id` = identifiant métier, `position`, taux
  `NUMERIC(19,8)`), `CreditLoanRepository` (`findAllByOrderByPositionAsc`) et `CreditLoanEntityMapper`
  (`LoanModel` ↔ `CreditLoanEntity`, sans perte, informations du contrat bancaire incluses). Aucune relation vers
  `BudgetDataEntity` ; `LoanEntity`, `EntityModelConverter`, `LoanPersistenceAdapter` et le hub sont **inchangés**
  (chemin legacy intact, aucune donnée migrée). La nouvelle table est créée vide par `ddl-auto` et reste inutilisée
  jusqu'à DB-1041. Le nom `Loan*` est déjà pris par le chemin legacy, d'où le préfixe `CreditLoan*`.
- **Tests** : `CreditLoanJpaModelTest` (H2, `@DataJpaTest`) : aller-retour avec ordre conservé, taux non arrondi,
  champs du contrat absents conservés à `null`, remplacement de la liste, refus d'un prêt sans identifiant.
- **Pour DB-1041** : reproduire le schéma de DB-1021 (lecture JPA, recopie par `BudgetPersistenceGateway` dans la
  transaction de sauvegarde, reconstruction depuis le hub au chargement du cache) ; la table `credit_loan` devra
  être vidée/réécrite avec `flush` entre suppression et réinsertion (clé primaire métier).

### Statut DB-1041 — JPA Crédit — basculer l'adapter

- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [x] Terminé
- **Livré** : `LoanPersistenceAdapter.getLoans()` lit désormais `CreditLoanRepository` (table `credit_loan`) via
  `CreditLoanEntityMapper`. Les écritures (`saveLoanRow`, `deleteLoanRow`) restent portées par `PersistenceManager` ;
  `BudgetPersistenceGateway` recopie les prêts du modèle dans la table autonome à chaque sauvegarde, dans la même
  transaction (import, reset et mutations de tous domaines inclus : rollback cohérent). Au chargement du cache
  (démarrage), la table est reconstruite depuis le hub, ce qui migre les données existantes sans script.
  `CreditLoanRepository` est injecté dans `PersistenceManager` (constructeur élargi ; fabrique et tests adaptés).
- **Retour arrière** : le hub (`loan`) reste alimenté et reste la source de chargement du cache. Le constructeur
  `LoanPersistenceAdapter(PersistenceManager)` conserve la lecture depuis le cache (tests unitaires à repositories
  mockés) ; revenir au comportement antérieur en production consiste à revenir sur ce patch.
- **Tests** : `PersistenceAdaptersJpaRoundTripTest` (H2) : prêts d'un import relus par JPA, création / mise à jour
  (taux non arrondi) / suppression visibles via la lecture JPA, reconstruction de la table au démarrage, reset.
  Constructeurs de `PersistenceManager` adaptés dans `PersistenceManagerTestFactory`, `WriteFailureKeepsMemoryTest`
  et le round-trip.
- **Reste** : `VT-320` (redémarrage, H2 + PostgreSQL) couvre déjà `POST /patrimoine/loans` ; à exécuter en CI. La
  suppression de la relation du hub relève de DB-1140.

## DB-1050 / DB-1051 — JPA Patrimoine

- **DB-1050** : placements, immobilier, catégories et historiques nécessaires, sans suppression du hub.
- **DB-1051** : bascule des adapters Patrimoine.
- **Validation** : `VT-220` obligatoire après bascule : Patrimoine → Trésorerie → Overview.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

## DB-1060 / DB-1061 — JPA Trésorerie

- **DB-1060** : entités/repositories Trésorerie additifs.
- **DB-1061** : bascule adapter et tests.
- **Validation** : `VT-110` obligatoire pour protéger le graphe Retraite → Fiscalité → Trésorerie → Overview.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

## DB-1070 — Vérifier les readers après bascule JPA

- **Prérequis** : DB-1001, DB-1011, DB-1021, DB-1031, DB-1041.
- **Objectif** : vérifier qu'aucun service applicatif n'est revenu à `getBudgetData()` pour compenser une migration.
- **Travaux** : recherche statique + ArchUnit + tests d'intégration.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

## DB-1080 — Vérifier les parcours E2E après bascule JPA

- **Prérequis** : DB-1051, DB-1061, `VT-220`, `VT-320`.
- **Objectif** : prouver que le changement de persistance reste invisible fonctionnellement.
- **Travaux** : F1/F2/F3/F4 de `16-tests.md`, fallback désactivé.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

---

## DB-1100 à DB-1160 — Suppression séquentielle des relations du hub

Ces patchs sont volontairement courts et séquentiels. Ils ont plus de conflit potentiel car ils modifient le même
`BudgetDataEntity`.

Pour chaque patch :

- supprimer uniquement les relations du domaine concerné devenues inutiles ;
- supprimer les cascades/orphan removal associés seulement après vérification ;
- compiler ;
- exécuter les tests PostgreSQL du domaine ;
- rechercher les accès ORM au champ supprimé.

L'ordre exact est : Retraite → Fiscalité → Objectifs → Banque → Crédit → Patrimoine → Trésorerie. Il ne constitue
pas une hiérarchie métier ; il minimise les risques en commençant par les domaines déjà basculés et les sous-graphes
les plus isolés.

## DB-1170 — Nettoyer `EntityModelConverter` en mappers par domaine

- **Prérequis** : DB-1160.
- **Objectif** : retirer la dépendance technique globale après disparition du hub.
- **Travaux** : un mapper/converter par owner ; supprimer les méthodes mortes ; laisser les conversions du snapshot
  global dans un composant explicitement transverse.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

## DB-1180 — Réduire `BudgetDataModel` au snapshot global

- **Prérequis** : DB-1170, `VT-500`.
- **Objectif** : empêcher l'utilisation de `BudgetDataModel` comme façade d'accès quotidien à la persistance.
- **Travaux** : recherche des `new BudgetDataModel(...)`, `getBudgetData()`, `setBudgetData()` ; conserver seulement
  les chemins import/export/backup/migration/tests globaux.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

## DB-1190 — Nettoyage final du bootstrap/persistence legacy

- **Prérequis** : DB-1180.
- **Objectif** : supprimer le code de transition qui n'a plus d'appelant.
- **Travaux** : anciens repositories, gateways legacy, méthodes inutilisées, wiring manuel restant.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

## DB-1200 — Gate persistance avant Maven

- **Prérequis** : DB-011, DB-061, DB-1080, DB-1190, `VT-320`, `VT-330`, `VT-340`, `VT-350`.
- **Objectif** : autoriser la création des modules uniquement lorsque les frontières sont réellement exploitables.
- **Contrôles** : build, PostgreSQL, restart, E2E fallback désactivé, ArchUnit, recherche des références résiduelles,
  checklist `14-checklist-maven.md`.
- **Livrable** : validation humaine + passage vers le futur backlog Maven.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

## Graphe de parallélisation

```text
                              DB-000
                                 │
                    ┌────────────┼────────────┐
                    ↓            ↓            ↓
                 DB-010      DB-020…041     DB-061
                    │            │            │
                    ↓            ↓            ↓
                 DB-011       DB-050 ─────→ DB-060
                                               │
          ┌───────────────┬───────────────┬────┴──────────────┐
          ↓               ↓               ↓                   ↓
      Retraite        Fiscalité        Objectifs           Banque
      1000/1001       1010/1011       1020/1021            1030/1031
          │               │               │                   │
          └───────────────┴───────────────┴───────────────────┤
                                                              ↓
                         Crédit 1040/1041 ──┐
                                             ├──→ DB-1070
                  Patrimoine 1050/1051 ─────┤
                  Trésorerie 1060/1061 ──────┘
                                             │
                                           DB-1080
                                             │
                                   ┌─────────┴─────────┐
                                   ↓                   ↓
                                hub cleanups        autres tests
                              1100→1110→...→1160
                                   │
                                  DB-1170
                                   │
                                  DB-1180
                                   │
                                  DB-1190
                                   │
                                  DB-1200
                                   ↓
                             modules Maven
```

Le gain de parallélisation recherché vient principalement de la phase additive et de la bascule par domaine. Les
modifications du hub sont la seule zone volontairement sérialisée ; elles doivent rester courtes grâce aux deux
phases précédentes.
