# 26 — Séquençage de R-61 (suppression du hub et du modèle global)

Statut : 🟡 premier jet (établi le 25 octobre 2026, à partir d'une exploration complète de
`back/persistence`, `back/transition-snapshot`, `back/server` et `back/application` sur `main`)

## 1. Pourquoi ce document

[R-61] (`24-backlog-reste-a-faire.md`) est un seul item de backlog, mais ce n'est pas un seul
patch : l'exploration menée pour ce document a trouvé **46 fichiers de test** et **~20 fichiers
de production** qui dépendent encore, d'une façon ou d'une autre, de `back/persistence` ou
`back/transition-snapshot`. Tenter de livrer ça en un seul `.patch`, sans compilateur
disponible côté agent, est le meilleur moyen de livrer un patch qui ne s'applique pas ou qui
casse `mvn clean test` sur un périmètre impossible à vérifier à la main.

Ce document découpe R-61 en **27 sous-patchs** (`R-61-01` à `R-61-27`), chacun pensé pour être
pris par un agent différent, avec peu de tokens. Règle du jeu, actée avec Marco le 25 octobre
2026 :

- **un sous-patch peut laisser le dépôt dans un état qui ne compile pas** — l'agent suivant
  reprend où le précédent s'est arrêté ; ce n'est pas une erreur à corriger dans le même patch ;
- en contrepartie, **chaque sous-patch reste petit** (idéalement un seul fichier, quelques-uns
  au plus) et **documente dans ce fichier** (tableau section 4) ce qu'il a fait et l'état dans
  lequel il laisse le dépôt, pour que l'agent suivant n'ait pas à deviner ;
- l'ordre des sous-patchs n'est pas arbitraire (voir section 3) : il minimise les allers-retours,
  mais ne garantit pas un dépôt vert à chaque étape intermédiaire, seulement aux jalons marqués
  **« vert attendu »** ;
- un sous-patch qui tombe sur un arbitrage non tranché devient « bloqué » et s'arrête (DA-13,
  `23-registre-decisions-actives.md`) — deux points de ce genre sont déjà anticipés et tranchés
  ci-dessous (sections 3.2 et 3.11) pour éviter un blocage inutile.

Ce fichier est la fiche de démarrage de **chaque** sous-patch R-61-xx, au même titre que
`25-cartographie-modules-patchs.md` pour le reste du backlog : un agent qui prend un sous-patch
lit uniquement la ligne de la section 4 qui le concerne, plus le rappel de méthode en section 3.2
si son sous-patch touche à des tests.

## 2. Ce que l'exploration a établi (à ne pas refaire)

- Hors de `back/persistence` et `back/transition-snapshot` eux-mêmes, **aucun fichier `main` ne
  dépend réellement** de `PersistenceManager`, `BudgetCacheStore`, `BudgetMutationService`,
  `BudgetPersistenceGateway`, `EntityModelConverter`, `DomainMutations` ou
  `LegacyObjectifAllocationMigrator` : grep sur les imports réels (`^import
  com\.moe\.myfamilybudget\.persistence\.`) ne trouve que des mentions en javadoc. Ce sont des
  classes mortes pour la production, gelées dans leur module jusqu'à SILO-230 (voir
  `BudgetDataModelAllowList`, 5 entrées).
- Seuls **deux usages réels** de `transition-snapshot` subsistent hors du hub, tous deux
  triviaux à couper (section 3.1) :
  - `OverviewMapper` construit un `SettingsModel` juste pour relire ses champs un par un à
    partir d'un `SettingsDto` déjà disponible — l'intermédiaire ne sert à rien ;
  - `TresorerieServiceImpl` importe `transition.error.UnknownTresorerieFieldException`, une
    sous-classe **dépréciée depuis SILO-216** de la vraie exception, déjà disponible dans
    `domain.treasury.port`.
- `BudgetMutatedEvent` (publié uniquement par `DomainMutations`/`PersistenceManager`, donc
  jamais en production aujourd'hui) a un unique abonné mort,
  `NotificationBudgetMutationListener.onBudgetMutated` : chaque silo publie désormais son
  propre `<Silo>MutatedEvent`, déjà écouté par les sept autres méthodes de ce même listener
  (DA-08).
- Le script Liquibase de suppression est préparé par une table déjà connue : le changelog
  `back/persistence/src/main/resources/db/changelog/persistence/persistence-legacy.changelog.xml`
  documente précisément les 4 tables encore legacy (`budget_data`, `placement`,
  `placement_history_entry`, `settings`, cette dernière réduite à sa seule colonne `id` depuis
  R-51) et leurs FK. Ce fichier **doit être déplacé vers `back/server`** avant la suppression du
  module `persistence` (un changelog déjà exécuté ne doit jamais disparaître du classpath, sous
  peine de casser la validation Liquibase au prochain démarrage) — voir R-61-23.
- 46 fichiers de test (listés section 4) importent encore `com.moe.myfamilybudget.persistence.*`
  ou `com.moe.myfamilybudget.transition.*`. La majorité n'utilise ces classes que comme mécanisme
  d'amorçage de données (fixture), pas comme sujet du test lui-même — voir la grille de décision
  en 3.2.

## 3. Méthode

### 3.1. Phase A — débrancher les deux usages réels (R-61-01 à R-61-03)

Spécifié intégralement ci-dessous (section 4), rien à trancher :

- **R-61-01** : dans `NotificationBudgetMutationListener`, supprimer la méthode
  `onBudgetMutated(BudgetMutatedEvent event)` et l'import de `BudgetMutatedEvent` ; dans
  `NotificationBudgetMutationListenerTest`, supprimer le test correspondant (et son import).
- **R-61-02** : dans `OverviewMapper.toSnapshotFragments`, remplacer
  `SettingsModel settings = toSettingsModel(dto.getSettings());` par
  `SettingsDto settingsDto = dto.getSettings();` et chaque `settings.xxx()` par
  `settingsDto.getXxx()` (mapping 1:1, déjà vérifié) ; supprimer la méthode privée
  `toSettingsModel` et l'import de `transition.model.SettingsModel` ; dans le javadoc de
  `toObjectifsParameters`, retirer le `{@link SettingsModel}` devenu invalide.
- **R-61-03** : dans `TresorerieServiceImpl`, remplacer l'import
  `com.moe.myfamilybudget.transition.error.UnknownTresorerieFieldException` par
  `com.moe.myfamilybudget.domain.treasury.port.UnknownTresorerieFieldException` (même nom de
  classe, le constructeur `(listKey, field)` est identique).

**Jalon « vert attendu »** après R-61-03 : plus aucun fichier `main` hors de `back/persistence`
et `back/transition-snapshot` ne doit apparaître dans
`grep -rln "com\.moe\.myfamilybudget\.persistence\.\|com\.moe\.myfamilybudget\.transition\." back/application/src/main back/server/src/main`.

### 3.2. Grille de décision pour un fichier de test (Phase C, R-61-06 à R-61-21)

Chaque sous-patch de cette phase applique, fichier par fichier, la grille suivante — **dans cet
ordre** :

1. Le fichier teste directement une classe qui disparaît (nom du fichier = `<Classe>Test.java`
   où `<Classe>` est une des classes listées en section 2, ou `BudgetDataEntity`,
   `PlacementEntity`, `PlacementHistoryEntryEntity`, `BudgetDataRepository`,
   `PlacementRepository`) → **supprimer le fichier entier**.
2. Le fichier est un test ArchUnit dont la règle n'a plus d'objet une fois la classe qu'elle
   contraint absente (ex. `BudgetDataModelUsageArchTest` contrôle `BudgetDataModelAllowList`)
   → **supprimer le fichier entier**, sauf si la règle couvre aussi autre chose (vérifier le
   contenu ligne à ligne avant de conclure — plusieurs fichiers `architecture/*.java` de cette
   liste contiennent probablement plusieurs règles indépendantes : dans ce cas, **édition
   ciblée**, pas suppression du fichier).
3. Le fichier teste un service de production toujours vivant et n'utilise
   `PersistenceManager`/`BudgetDataModel`/`SettingsModel` **que pour amorcer des données de
   test** (fixture), le test lui-même portant sur autre chose → **réécrire l'amorçage** pour
   passer uniquement par les `InMemory<Silo>Store` déjà présents dans `testsupport`, sans créer
   de `BudgetDataModel` ni de `PersistenceManager`. Patron de référence : la méthode
   `setBudgetData(...)` d'`OverviewServiceImplTest` redistribue déjà `budgetData` vers
   `treasuryStore.replace(...)`, `wealthStore.replace(...)` et `retirementStore.replace(...)` —
   il suffit de supprimer l'appel `persistenceManager.setBudgetData(budgetData)` restant et de
   construire les mêmes valeurs directement avec les modèles de silo (`TresorerieSettingsModel`,
   `RetirementSettingsModel`, etc.), sans passer par `BudgetDataModel`/`SettingsModel` comme
   étape intermédiaire. Remplacer aussi `CacheBackedAppSettings.of(persistenceManager)` par la
   nouvelle forme sans argument introduite en R-61-04.
4. Aucun des cas ci-dessus ne s'applique clairement → le sous-patch note l'incertitude dans son
   compte-rendu (section 4) plutôt que de deviner, et peut se limiter à un sous-ensemble du lot
   si un fichier s'avère plus gros ou plus entortillé que prévu (ex. `PersistenceAdaptersJpaRoundTripTest.java`,
   1152 lignes, ou `BusinessLogicIntegrationTest.java`, 918 lignes : un agent qui ouvre un de ces
   deux fichiers et constate qu'il a besoin de plusieurs sous-patchs pour le vider proprement est
   invité à le scinder lui-même — renommer son sous-patch en `R-61-xx-a`, `R-61-xx-b`, etc. et le
   signaler dans son compte-rendu).

`PersistenceManagerTestFactory` (`testsupport`) ne doit être supprimé qu'**en dernier**, une fois
qu'aucun des 46 fichiers ne l'appelle plus (R-61-21).

### 3.3. Point d'arbitrage déjà tranché — `BudgetPersistenceGatewayRepeatedSaveTest` (R-61-05)

`R-30b` (`24-backlog-reste-a-faire.md`) avait laissé ouvert le sort de ce test (non-orphelinage
d'enfants JPA sur `save` répété dans la même transaction, SILO-119 B2) : le garder comme
« dernier témoin du hub » jusqu'à R-61, ou le migrer plus tôt vers un autre domaine à collection
enfant. R-61 supprime le hub en bloc, donc la question ne peut plus être différée.

**Décision prise dans ce document** : migrer le scénario vers `goals-core`
(`GoalEntity`/`GoalAllocationEntity`, relation `@OneToMany` déjà en place depuis SILO-212) plutôt
que d'accepter une perte de couverture silencieuse. Si l'agent qui prend R-61-05 constate qu'aucun
silo encore actif n'a de collection enfant comparable (à vérifier : Objectifs est le candidat le
plus probable, Trésorerie/Crédit à défaut), il peut conclure à l'impossibilité et supprimer le
test sans migration — mais **doit** le dire explicitement dans son compte-rendu (section 4, ligne
R-61-05) plutôt que de supprimer silencieusement.

## 4. Table des sous-patchs

Mêmes colonnes que `24-backlog-reste-a-faire.md`. **Qui** et **Branche / commit** à renseigner
par l'agent qui prend le sous-patch. La colonne **Notes de passation** est propre à ce document :
c'est elle que l'agent suivant doit lire en premier (état rouge/vert, ce qui a été fait).

### Phase A — débrancher les deux usages réels hors du hub (vert attendu après R-61-03)

| ID | Dépend de | Fichiers | Action | Statut | Qui | Branche / commit | Notes de passation |
|---|---|---|---|---|---|---|---|
| R-61-01 | — | `NotificationBudgetMutationListener.java`, `NotificationBudgetMutationListenerTest.java` | Retirer `onBudgetMutated`/`BudgetMutatedEvent` (méthode + import) des deux fichiers | Livré | Agent | — | Fait tel que spécifié, aucun écart |
| R-61-02 | — | `OverviewMapper.java` | Remplacer `SettingsModel` par `SettingsDto` dans `toSnapshotFragments` (voir 3.1) | Livré | Agent | — | Fait tel que spécifié ; `toObjectifsParameters` : le `{@link SettingsModel}` du javadoc remplacé par du texte simple |
| R-61-03 | — | `TresorerieServiceImpl.java` | Basculer vers `domain.treasury.port.UnknownTresorerieFieldException` | Livré | Agent | — | Fait tel que spécifié. **Jalon « vert attendu » de la Phase A confirmé** : `grep -rln "com\.moe\.myfamilybudget\.persistence\.\|com\.moe\.myfamilybudget\.transition\." back/application/src/main back/server/src/main` renvoie vide |

### Phase B — préparer le testsupport partagé

| ID | Dépend de | Fichiers | Action | Statut | Qui | Branche / commit | Notes de passation |
|---|---|---|---|---|---|---|---|
| R-61-04 | — | `CacheBackedAppSettings.java` | Remplacer `of(PersistenceManager)` par une fabrique sans argument (ex. `create()` ou constructeur public) ; le repli `simulateUntilAge`/`inflationRate` devient une valeur par défaut en dur (85 / `BigDecimal("0.02")`, mêmes défauts que `BudgetDataModel.getEffectiveSettings()`) au lieu de relire `persistenceManager.getBudgetData()`. **Ce patch casse volontairement** tous les appelants encore sur `of(persistenceManager)` (Phase C) : état rouge accepté, à corriger au fil des sous-patchs de la Phase C qui touchent chaque appelant | Livré | Agent | — | Constructeur public sans argument retenu (pas de fabrique `create()`, directement `new CacheBackedAppSettings()`, plus simple). **15 fichiers cassés comme prévu**, listés dans le commit : `SettingsCommandRouterTestFactory`, `BusinessLogicIntegrationTest`, `DefaultRetirementUseCaseTest`, `RetraiteServiceImplTest`, `PendingOperationsServiceImplTest`, `PointageServiceImplIntegrationTest`, `StatementBankImportServiceImplTest`, `ImpotsServiceImplTest`, `AnalyseServiceImplTest`, `ParametersServiceImplTest`, `PointageServiceImplTest`, `TresorerieServiceImplTest`, `StatementBankImportServiceImplIntegrationTest`, `PatrimoineServiceImplTest`, `OverviewServiceImplTest` — chacun aura besoin d'un `of(x) -> new CacheBackedAppSettings()` en plus de son travail propre quand son tour viendra (R-61-14 à R-61-21) |
| R-61-05 | — | `BudgetPersistenceGatewayRepeatedSaveTest.java` (supprimé), `GoalJpaModelTest.java` (silo `goals-core`) | Trancher et appliquer la migration du scénario de non-orphelinage (voir 3.3) avant suppression du fichier | Livré | Agent | — | Décision appliquée : `goals-core`/`GoalEntity` convenait (relation `@OneToMany(orphanRemoval = true)` vers `GoalAllocationEntity`, `JpaGoalStore.replace()` fait le même `deleteAll()`+`flush()` puis ré-insertion que l'ancien `save()` du hub). Les 2 scénarios de non-orphelinage (sauvegardes successives, puis save/reset/save, dans une même transaction) migrés en 2 nouvelles méthodes de test dans `GoalJpaModelTest.java` (déjà `@DataJpaTest`, vraie base H2 — pas `JpaGoalStoreTest.java` qui utilise un repository en mémoire et ne reproduirait pas le bug Hibernate d'origine). Le 3ᵉ scénario (« sauver le modèle global ne touche pas la table Crédit ») n'a **pas** été migré : il est devenu structurellement impossible à violer (chaque silo n'a plus qu'un repository sur sa propre table, il n'existe plus de « modèle global » qui pourrait toucher une autre table) — perte de couverture jugée nulle, assumée explicitement ici plutôt que silencieusement |

### Phase C — nettoyage des 46 fichiers de test (petits lots, état rouge toléré entre deux lots)

| ID | Dépend de | Fichiers | Action (grille 3.2) | Statut | Qui | Branche / commit | Notes de passation |
|---|---|---|---|---|---|---|---|
| R-61-06 | R-61-01..05 | `persistence/BudgetCacheStoreRollbackTest.java`, `persistence/RatePrecisionPersistenceTest.java` | Cas 1 a priori (à confirmer en ouvrant les fichiers) | Livré | Agent | — | Cas 1 confirmé pour les deux fichiers, supprimés entièrement, aucune réécriture. `BudgetCacheStoreRollbackTest` teste directement `BudgetCacheStore`/`BudgetPersistenceGateway` (VT-340) ; couverture rollback déjà assurée hors hub par `MultiDomainAtomicityTest` (silos actifs), aucune migration jugée nécessaire. `RatePrecisionPersistenceTest` teste directement `PlacementEntity` (taux non arrondis) ; `WealthPlacementEntity` (DB-1050, additif, pas encore branché) est déjà couvert à l'identique par `WealthJpaModelTest.placementRatesAreNotRounded` et `placementsRoundTrip` (3 taux sur 8 décimales) — perte de couverture nulle, pas de test à écrire |
| R-61-07 | R-61-06 | `persistence/UpdateTaxSettingsMinimalBudgetTest.java`, `persistence/WriteFailureKeepsMemoryTest.java` | À vérifier : `WriteFailureKeepsMemoryTest` peut chevaucher la migration déjà faite vers Retraite notée dans R-30b — lire avant de conclure (cas 1 ou 4) | Livré | Agent | — | Cas 1 pour les deux fichiers (instancient directement `PersistenceManager`), supprimés entièrement. `UpdateTaxSettingsMinimalBudgetTest` (FIX-020, bug d'unboxing `Boolean sweepEnabled` dans l'ancien dispatcher) : le bug n'existe plus dans le chemin de production actuel (`SettingsCommandRouter` + `TresorerieCommandService`/`RetirementCommandService`/`TaxCommandService`, `JpaTreasuryStore.toBoolean(value)` sans ternaire sur primitif) ; routage par owner déjà couvert par `SettingsCommandRouterTest` + les 3 `*CommandServiceTest`. `WriteFailureKeepsMemoryTest` (VT-330, échec DB laisse la mémoire du cache hub inchangée, cf. commentaire de classe sur R-40/R-30b : s'appuyait déjà sur les placements comme dernier témoin) : pas de chevauchement avec la migration Retraite de R-30b (celle-ci concernait `BudgetPersistenceGatewayRepeatedSaveTest`, déjà traité en R-61-05) ; le scénario protégé devient structurellement impossible dans la nouvelle architecture (plus de cache mémoire séparé de la base dans les stores JPA des silos actifs, lecture directe à chaque appel) — même raisonnement que le 3ᵉ scénario écarté en R-61-05, perte de couverture assumée explicitement, aucun test de remplacement |
| R-61-08 | R-61-07 | `persistence/adapter/InterSiloIntegrityCharacterizationTest.java`, `persistence/adapter/ReadersDoNotUseBudgetCacheTest.java`, `persistence/adapter/SnapshotFragmentWritersTest.java` | À classer à l'ouverture (cas 1 ou 4) | À faire | | | |
| R-61-09 | R-61-08 | `persistence/adapter/PersistenceAdaptersJpaRoundTripTest.java` (1152 lignes) | Gros fichier : probable scission en plusieurs sous-patchs `R-61-09-a`, `-b`, ... (voir 3.2 point 4) | À faire | | | |
| R-61-10 | R-61-08 | `persistence/adapter/PersistenceAdaptersTest.java` (282 lignes) | À classer à l'ouverture | À faire | | | |
| R-61-11 | R-61-02 | `architecture/ApplicationAndServerWithoutBudgetDataModelArchTest.java`, `architecture/BudgetDataModelUsageArchTest.java` | Cas 2, suppression a priori (règles entièrement dédiées à `BudgetDataModel`/`BudgetDataModelAllowList`) | À faire | | | |
| R-61-12 | R-61-11 | `architecture/CalculationDependenciesArchTest.java`, `architecture/GlobalSnapshotBoundaryArchTest.java`, `architecture/PureLayerRules.java` | Cas 2, édition ciblée probable (fichiers de règles partagées, ne pas supprimer sans lire) | À faire | | | |
| R-61-13 | R-61-12 | `architecture/ReaderPersistenceBoundaryArchTest.java`, `architecture/ResultModelsArchTest.java`, `calculation/archfixture/EngineWithBudgetDataModelFixture.java`, `calculation/archfixture/EngineWithPersistenceEntityFixture.java` | Les deux fixtures (8 lignes chacune) suivent le sort du test qui les utilise (probablement `CalculationDependenciesArchTest`, R-61-12) | À faire | | | |
| R-61-14 | R-61-02, R-61-03 | `command/GoalCommandServiceTest.java`, `factory/PatrimoineInputFactoryTest.java`, `factory/TreasuryInputFactoryTest.java`, `factory/OverviewInputFactoryTest.java` | À classer à l'ouverture (import count faible, probablement cas 3 ou mention isolée) | À faire | | | |
| R-61-15 | R-61-04 | `service/AnalyseServiceImplTest.java`, `service/ExcelToCsvServiceTest.java`, `service/ImpotsServiceImplTest.java`, `service/ParametersServiceImplTest.java` | Cas 3, patron `OverviewServiceImplTest` (voir 3.2) | À faire | | | |
| R-61-16 | R-61-04 | `service/OverviewServiceImplTest.java`, `snapshot/GlobalBudgetSnapshotServiceTest.java`, `usecase/DefaultRetirementUseCaseTest.java` | Cas 3 ; `OverviewServiceImplTest` est l'exemple déjà analysé en détail (3.2) | À faire | | | |
| R-61-17 | R-61-04 | `service/PatrimoineServiceImplTest.java` (818 lignes), `service/PendingOperationsServiceImplTest.java`, `service/PointageServiceImplIntegrationTest.java`, `service/PointageServiceImplTest.java` | Cas 3 ; scinder si `PatrimoineServiceImplTest` s'avère trop gros pour un seul sous-patch | À faire | | | |
| R-61-18 | R-61-04 | `service/RetraiteServiceImplTest.java`, `service/StatementBankImportServiceImplIntegrationTest.java`, `service/StatementBankImportServiceImplTest.java`, `service/TresorerieServiceImplTest.java` (634 lignes) | Cas 3 | À faire | | | |
| R-61-19 | R-61-04 | `integration/BusinessLogicIntegrationTest.java` (918 lignes) | Cas 3, probable scission (voir 3.2 point 4) | À faire | | | |
| R-61-20 | R-61-19 | `integration/ConcurrentMutationsApiTest.java`, `integration/MultiDomainAtomicityTest.java` | Cas 3 | À faire | | | |
| R-61-21 | R-61-06..20 | `model/AnalyseCalculatorTest.java`, `testsupport/SettingsCommandRouterTestFactory.java`, `testsupport/PersistenceManagerTestFactory.java` | `PersistenceManagerTestFactory` en dernier (3.2, dernier paragraphe) : vérifier qu'aucun fichier des lots précédents ne l'appelle plus avant de le supprimer | À faire | | | |

**Jalon « vert attendu »** après R-61-21 : `mvn clean test` doit passer, et plus aucun fichier
(main ou test) hors de `back/persistence` et `back/transition-snapshot` ne doit référencer ces
deux packages.

### Phase D — suppression du code de production du hub

| ID | Dépend de | Fichiers | Action | Statut | Qui | Branche / commit | Notes de passation |
|---|---|---|---|---|---|---|---|
| R-61-22 | R-61-21 | `back/persistence/src/main/resources/db/changelog/persistence/persistence-legacy.changelog.xml` | `git mv` (déplacement pur, aucun contenu modifié) vers `back/server/src/main/resources/db/changelog/persistence/persistence-legacy.changelog.xml`. Le chemin référencé dans `db.changelog-master.xml` (`db/changelog/persistence/persistence-legacy.changelog.xml`, relatif au classpath) ne change pas, seul l'emplacement physique du module change — aucune édition de `db.changelog-master.xml` nécessaire. **À faire avant R-61-24** (le fichier doit survivre à la suppression du module `persistence`) | À faire | | | |
| R-61-23 | R-61-21 | `back/transition-snapshot/` entier | Supprimer le module : `pom.xml`, tout `src/main/java/**` (`BudgetDataModel`, `SettingsModel`, `SettingsReader`, `UnknownTresorerieFieldException`, `module-info.java`), retirer `<module>transition-snapshot</module>` de `back/pom.xml`, retirer la dépendance `transition-snapshot` (et son commentaire) de `back/application/pom.xml` | À faire | | | |
| R-61-24 | R-61-22, R-61-23 | `back/persistence/` entier | Supprimer le module : `pom.xml` et les 14 fichiers Java restants (`BudgetCacheStore`, `BudgetMutatedEvent`, `BudgetMutationService`, `BudgetPersistenceGateway`, `DomainMutations`, `PersistenceManager`, `adapter/PatrimoinePersistenceAdapter`, `converter/EntityModelConverter`, `entity/BudgetDataEntity`, `entity/PlacementEntity`, `entity/PlacementHistoryEntryEntity`, `migration/LegacyObjectifAllocationMigrator`, `repository/BudgetDataRepository`, `repository/PlacementRepository`), retirer `<module>persistence</module>` de `back/pom.xml`, retirer la dépendance `persistence` de `back/server/pom.xml` | À faire | | | |
| R-61-25 | R-61-24 | `BudgetDataModelAllowList.java` et les ArchUnit restants qui la consultaient | Supprimer `BudgetDataModelAllowList` (ou la vider et figer `FROZEN_SIZE=0` si un test doit encore constater l'absence de toute entrée) ; nettoyer les imports orphelins laissés dans les fichiers édités en R-61-12/R-61-13 | À faire | | | |

**Jalon « vert attendu »** après R-61-25 : `mvn clean test` doit passer sans `back/persistence`
ni `back/transition-snapshot` dans le reactor.

### Phase E — script Liquibase de suppression

| ID | Dépend de | Fichiers | Action | Statut | Qui | Branche / commit | Notes de passation |
|---|---|---|---|---|---|---|---|
| R-61-26 | R-61-25 | `persistence-legacy.changelog.xml` (déplacé en R-61-22) | Ajouter, **à la suite** des changeSets existants (ne jamais modifier `r01-persistence-*`/`r51-persistence-*`, déjà exécutés), 4 nouveaux changeSets `author="R-61"` : `dropTable` sur `placement_history_entry`, puis `placement`, puis `budget_data`, puis `settings`, chacun avec `cascadeConstraints="true"` (respecte DA-02 : pas de FK inter-silo à préserver, DA-03 : migration par script) | À faire | | | |

### Phase F — clôture

| ID | Dépend de | Fichiers | Action | Statut | Qui | Branche / commit | Notes de passation |
|---|---|---|---|---|---|---|---|
| R-61-27 | R-61-26 | `24-backlog-reste-a-faire.md`, `25-cartographie-modules-patchs.md`, `23-registre-decisions-actives.md`, ce fichier | Passer la ligne R-61 de `24` à *Fait !* ; retirer la référence à ce fichier dans `25` (la ligne R-61 n'a plus de dossiers à cloner) ; archiver DA-04 et DA-12 dans `23` (section 4, « appliquées et verrouillées »), preuve = ce découpage ; marquer ce document `26` comme clos en en-tête | À faire | | | |

## 5. Rappel : critère de « Fait ! » pour R-61

La ligne R-61 de `24-backlog-reste-a-faire.md` ne passe à *Fait !* qu'au sous-patch R-61-27, pas
avant. Un agent qui termine un sous-patch intermédiaire met à jour **uniquement** la ligne
correspondante de la table ci-dessus (Statut, Qui, Branche, Notes de passation) ; il ne touche
pas au statut de R-61 dans `24`.
