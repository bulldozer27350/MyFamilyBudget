# 19 — Inventaire des usages de `BudgetDataModel` (CLEAN-010)

Statut : 🟢 livré avec `CLEAN-010`, mis à jour par `CLEAN-020`

Référence : `b91877c` (SET-040). Périmètre : `back/server/src/main/java` (production). Les tests ne sont pas
classés ici.

## Classification

| Code | Signification | Sort |
|---|---|---|
| `SNAPSHOT-GLOBAL` | Usage légitime : le modèle est le snapshot global (cache, import/export/reset, conversion entité ↔ modèle). | Conservé ; frontière explicite par `CLEAN-020`. |
| `ASSEMBLY-TEMP` | Assemblage applicatif transitoire : un service/factory/mapper recompose ou consomme le snapshot pour alimenter une factory ou une façade API. | Conservé pendant la transition ; disparaît avec la fin des `DB-xxx` et le passage des factories à des fragments. |
| `LOCAL-LEAK` | Fuite locale : le snapshot est exposé dans une signature qui n'a pas besoin d'être visible hors de sa classe. | Supprimé. |

## `new BudgetDataModel(...)` (15 sites en production, hors le constructeur de compatibilité du record)

| Fichier | Classification | Remarque |
|---|---|---|
| `persistence/converter/EntityModelConverter` | SNAPSHOT-GLOBAL | Entités → snapshot. |
| `persistence/BudgetCacheStore` | SNAPSHOT-GLOBAL | Publication du nouvel état en cache. |
| `persistence/BudgetPersistenceGateway` | SNAPSHOT-GLOBAL | Chargement depuis la base. |
| `snapshot/GlobalBudgetSnapshotService` (`composeSnapshot`) | SNAPSHOT-GLOBAL | Export / import / reset, isolés par `CLEAN-020` (ex-`SystemeServiceImpl`, qui ne référence plus le modèle). |
| `mapper/OverviewMapper` (`toInternalModel`, 2 sites) | SNAPSHOT-GLOBAL | Import JSON `/budget/import` ; le cas `dto == null` renvoie un snapshot vide. |
| `impl/OverviewServiceImpl` (`composeBudgetData`) | ASSEMBLY-TEMP | Alimente `OverviewInputFactory`. |
| `impl/AnalyseServiceImpl` (`composeBudgetData`) | ASSEMBLY-TEMP | Alimente `AnalyseInputFactory` / `BudgetFacadeView`. |
| `impl/AnalysePretsServiceImpl` (`composeBudgetData`) | ASSEMBLY-TEMP | Alimente `LoanAdviceInputFactory`. |
| `impl/ImpotsServiceImpl` (`composeBudgetData`) | ASSEMBLY-TEMP | Alimente `TaxInputFactory`. |
| `impl/PatrimoineServiceImpl` (`composeBudgetData`) | ASSEMBLY-TEMP | Alimente `PatrimoineInputFactory` / `PatrimoineMapper`. |
| `impl/PendingOperationsServiceImpl` (`composeBudgetData`) | ASSEMBLY-TEMP | Alimente `StatementBankImportMapper`. |
| `impl/TresorerieServiceImpl` (`composeBudgetData`) | ASSEMBLY-TEMP | Alimente `TreasuryInputFactory`. |
| `impl/RetraiteServiceImpl` (assemblage en ligne) | ASSEMBLY-TEMP | Alimente `RetirementInputFactory`. |
| `factory/RetirementInputFactory` | ASSEMBLY-TEMP | Repli sur un snapshot vide si `data == null`. |

## `getBudgetData()` / `setBudgetData(...)` (snapshot, hors accesseurs d'entités JPA)

| Fichier | Classification | Remarque |
|---|---|---|
| `persistence/PersistenceManager` | SNAPSHOT-GLOBAL | Façade de transition. |
| `persistence/BudgetCacheStore` | SNAPSHOT-GLOBAL | Source de vérité du cache. |
| `persistence/BudgetMutationService` | SNAPSHOT-GLOBAL | Lecture du `bankImport` du cache pour les mutations. |
| `persistence/adapter/*PersistenceAdapter` (Budget, Goal, Loan, Patrimoine, Retirement, Settings, Tax) | ASSEMBLY-TEMP | Lectures par fragment (`getEffective*`) via le cache ; disparaît adapter par adapter avec les `DB-xxx`. |
| `snapshot/GlobalBudgetSnapshotService` (`setBudgetData` à l'import, `resetData`) | SNAPSHOT-GLOBAL | Seul appelant applicatif hors persistance (`CLEAN-020`). |
| `persistence/entity/*Entity` (`getBudgetData()`) | hors périmètre | Accesseur vers `BudgetDataEntity`, sans rapport avec le modèle. |

## Autres consommateurs (signatures)

| Fichier | Classification | Remarque |
|---|---|---|
| `factory/*InputFactory`, `TaxSimulationPeriodResolver` (10 classes) | ASSEMBLY-TEMP | Assemblers applicatifs autorisés (voir `ARCH-010`). |
| `mapper/BudgetFacadeView` (`from`) | ASSEMBLY-TEMP | Seul point de passage snapshot → vue de façade composite (`RES-010`). |
| `mapper/PatrimoineMapper#toPatrimoineResponseDto` | ASSEMBLY-TEMP | Hors périmètre `RES-010`. |
| `mapper/StatementBankImportMapper#toPendingOperationsResponseMap` | ASSEMBLY-TEMP | Hors périmètre `RES-010`. |
| `mapper/OverviewMapper#toBudgetDataDto` | SNAPSHOT-GLOBAL | Export `/budget`. |
| `updater/TresorerieFieldUpdateDispatcher` | SNAPSHOT-GLOBAL | Mutation du snapshot du cache par `BudgetMutationService`. |
| `impl/TresorerieServiceImpl#computeTresorerie`, `buildCategoryOptions`, `computeRealAverages`, `buildTresorerieSuggestions` | LOCAL-LEAK → **supprimé** | Étaient `public` ; désormais visibles du seul package `impl`. |
| `impl/PatrimoineServiceImpl#computePatrimoineProjections` | LOCAL-LEAK → **supprimé** | Idem. |

Aucune référence à `BudgetDataModel` dans `calculation`, `notification` ni `model` hors Javadoc et le record
lui-même (garanti par `PureLayerArchTest` et `ResultModelsArchTest`). Aucune référence hors de
`com.moe.myfamilybudget.server.internal`.

## Garde-fou

`BudgetDataModelUsageArchTest` : seuls `..internal.persistence..`, `..internal.factory..`,
`..internal.mapper..`, `..internal.updater..`, `..internal.snapshot..` et les huit services d'API listés ci-dessus
peuvent dépendre de `BudgetDataModel`. Un nouveau consommateur fait échouer la règle : il doit consommer des fragments via les `Reader`
ou être ajouté à ce fichier et à la liste blanche avec sa classification.

## Dette restante (hors `CLEAN-010`)

- `composeBudgetData()` dupliqué dans sept services (et assemblé en ligne dans `RetraiteServiceImpl`) : à supprimer lorsque les factories consommeront des fragments
  (reprise des `DB-xxx`) ;
- opérations `/budget`, `/budget/import`, `/budget/reset` : isolées dans `internal.snapshot` par `CLEAN-020`
  (`GlobalBudgetSnapshotServiceTest`, `GlobalSnapshotBoundaryArchTest`).
