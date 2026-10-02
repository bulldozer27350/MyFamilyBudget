# 20 — Backlog de patchs pour la séparation du serveur en modules Maven

Statut : 🟡 à exécuter depuis `main` courant

Document établi à partir de l’état du dépôt au **2 octobre 2026** et des travaux d’architecture déjà réalisés.

## Objet

Ce backlog décrit le passage progressif du serveur monolithique Maven actuel vers un **reactor Maven multi-module**.

Le chantier peut commencer **sans attendre la fin de tous les `DB-xxx`** : les frontières logiques du domaine sont désormais
suffisamment explicites pour matérialiser progressivement les modules Maven, tandis que la persistance JPA continue à
évoluer dans son propre périmètre.

L’objectif n’est pas de transformer mécaniquement chaque package historique en module. Le découpage doit faire émerger
un **graphe de dépendances acyclique**, aligné sur les frontières métier déjà protégées par ArchUnit.

La stratégie retenue est :

1. créer le reactor sans déplacement massif ;
2. extraire les domaines déjà presque autonomes ;
3. extraire ensuite l’application et l’API ;
4. isoler enfin la persistance et le runtime Spring ;
5. terminer par une CI capable de limiter le build au sous-graphe nécessaire ;
6. ne considérer la séparation en plusieurs dépôts qu’après stabilisation du graphe Maven.

## Principes de découpage

- Maven doit matérialiser les frontières métier, pas reproduire les anciens packages `internal.*`.
- Un domaine ne doit pas dépendre d’un autre domaine via ses classes d’implémentation internes ; les échanges passent par des contrats/projections explicites.
- Les domaines purs ne dépendent ni de JPA, ni de `PersistenceManager`, ni des DTO OpenAPI, ni de Spring lorsqu’il n’est pas nécessaire.
- `BudgetDataModel` reste un **snapshot global de transition** ; il n’est pas introduit comme dépendance des nouveaux modules de domaine.
- `PersistenceManager`, `BudgetPersistenceGateway`, `BudgetCacheStore` et les entités JPA restent dans le périmètre persistance tant que les `DB-xxx` ne les ont pas rendus plus fins.
- Les `XxxInputFactory` sont considérées comme des composants d’**assemblage applicatif**, pas comme des composants du moteur métier pur.
- Les `ResultModel` métier restent dans les domaines ; les vues REST composites appartiennent à l’application/API.
- Chaque patch doit compiler et laisser la CI verte.
- Aucun patch Maven ne doit profiter de son déplacement pour modifier une règle métier ou un contrat REST sans item dédié.
- Les tests doivent être déplacés avec le code lorsque leur responsabilité est clairement locale au module.
- Les tests transverses Spring/E2E restent dans un module ou une source de test d’intégration approprié ; ils ne doivent pas être artificiellement dupliqués dans chaque domaine.

## État de départ connu

- Le backend reste actuellement un seul artifact `server` dans `back/server/pom.xml`.
- Les packages métier sont encore répartis transversalement dans `internal.calculation`, `internal.model`, `internal.port`, `internal.factory`, etc.
- Les moteurs principaux disposent déjà d’inputs/projections dédiés : Retraite, Fiscalité, Patrimoine, Trésorerie, Pointage, Analyse, Overview, Crédit et Notifications.
- Les règles ArchUnit couvrent désormais les interdictions de dépendances vers la persistance et les dépendances inter-domaines.
- `PersistenceManager` a été réduit mais reste un composant de transition.
- La persistance JPA est encore en migration (`DB-1000` à `DB-1180`).
- `BudgetDataModel` reste nécessaire à plusieurs assemblages globaux et chemins legacy ; son isolement physique final relève notamment de `DB-1180`.
- Le frontend est toujours embarqué dans le backend via Maven ; l’API OpenAPI est générée côté build du serveur.
- La CI actuelle exécute encore le backend complet, les tests JS, Playwright et le build Docker.

## Vue d’ensemble

| ID | Titre | Prérequis | Parallélisable avec | Bloquant pour les dépôts séparés |
|---|---|---|---|---|
| MAVEN-000 | Installer le parent Maven reactor | aucun | DB-xxx, MAVEN-010 | Non |
| MAVEN-010 | Extraire `domain-budget` | MAVEN-000 | MAVEN-020, DB-xxx | Non |
| MAVEN-020 | Extraire `domain-retirement` | MAVEN-010 | MAVEN-030, DB-xxx | Non |
| MAVEN-030 | Extraire `domain-tax` | MAVEN-020 | MAVEN-040 | Non |
| MAVEN-040 | Extraire `domain-wealth` | MAVEN-020 | MAVEN-050 | Non |
| MAVEN-050 | Extraire `domain-treasury` | MAVEN-030, MAVEN-040 | MAVEN-060 | Non |
| MAVEN-060 | Extraire `domain-bank-pointage` | MAVEN-010 | MAVEN-070, MAVEN-080 | Non |
| MAVEN-070 | Extraire `domain-analysis` | MAVEN-010, MAVEN-060 | MAVEN-080 | Non |
| MAVEN-080 | Extraire `domain-credit` et `domain-goals` | MAVEN-010, MAVEN-040 | MAVEN-090 | Non |
| MAVEN-090 | Extraire `domain-notifications` | MAVEN-060, MAVEN-080 | MAVEN-100 | Non |
| MAVEN-100 | Extraire `application` | MAVEN-020 à MAVEN-090 | MAVEN-110 | Oui |
| MAVEN-110 | Extraire `api` / OpenAPI généré | MAVEN-100 | aucun | Oui |
| MAVEN-120 | Extraire `persistence` | MAVEN-100 | MAVEN-110, DB-xxx | Oui |
| MAVEN-130 | Extraire `server-app` / composition root | MAVEN-100, MAVEN-110, MAVEN-120 | MAVEN-140 | Oui |
| MAVEN-140 | Stabiliser le graphe et ajouter les garde-fous Maven | MAVEN-100 à MAVEN-130 | DB-xxx | Oui |
| MAVEN-150 | CI sélective par sous-graphe Maven | MAVEN-140 | DB-xxx | Non |
| MAVEN-160 | Revue de candidatures au multi-repo | MAVEN-150 + DB-1180 | aucun | — |

---

# MAVEN-000 — Installer le parent Maven reactor

- **Prérequis** : aucun.
- **Objectif** : créer le parent Maven multi-module sans déplacer immédiatement le code.
- **Travaux** :
  - transformer `back/server/pom.xml` en parent reactor, ou introduire un parent racine dédié selon la structure retenue ;
  - définir les propriétés communes (Java 21, versions plugins, dépendances communes) ;
  - conserver la capacité de lancer le build complet depuis `back/server` ;
  - préparer les sections `modules`, `dependencyManagement` et `pluginManagement` ;
  - décider explicitement si le parent est packaging `pom` et si un module `server-app` porte l’exécutable final ;
  - ne pas introduire encore de déplacement massif de packages.
- **Décisions retenues** :
  - le parent est un **pom racine dédié** `back/pom.xml` (`myfamilybudget-parent`, packaging `pom`, hérite de `spring-boot-starter-parent` 3.4.2) ; `back/server/pom.xml` n'est pas transformé en parent, ce qui évite de déplacer `src/` ;
  - `back/server` (artifact `server`) reste le seul module qui porte du code et l'exécutable (`target/server-1.0.0-SNAPSHOT.jar`) : Dockerfile, CI, lanceurs `.bat`/`.sh` et gate VT-600 restent valides ; il deviendra le composition root `server-app` à MAVEN-130 (renommage/déplacement alors) ;
  - le parent ne porte que des versions (`properties`, `dependencyManagement`, `pluginManagement`) : aucune dépendance ni plugin activé, la configuration des plugins reste dans `server` ;
  - les nouveaux modules s'ajoutent à la section `<modules>` de `back/pom.xml` au fil des items MAVEN-010 à MAVEN-130 ;
  - `mvn -f back/server/pom.xml -B test` reste valide (parent résolu via `relativePath`) ; `mvn -f back/pom.xml -B test` construit le reactor complet (parent + `server`).
- **Critères de sortie** :
  - `mvn test` reste possible depuis `back/server` ;
  - le reactor est visible avec plusieurs modules, même si un seul contient encore le code applicatif ;
  - les plugins existants ne changent pas de comportement fonctionnel.
- **Tests** : build Maven complet ; génération OpenAPI ; packaging ; test de lancement du jar existant.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [x] Terminé

# MAVEN-010 — Extraire `domain-budget`

- **Prérequis** : MAVEN-000.
- **Objectif** : isoler les modèles du budget de base qui peuvent être utilisés par plusieurs domaines sans tirer la persistance.
- **Contenu cible** :
  - `internal.model.IncomeModel`
  - `ChargeModel`
  - `OneOffExpenseModel`
  - `TransferModel`
  - `VariableIncomeModel`
  - `VariableOverrideModel`
  - modèles simples réutilisés comme `CashflowYearModel`, `TripleAmountModel`, `CategoryOptionModel`, `RealAverageModel`, `VariablePreviewModel`, `VariablePreviewCellModel` lorsque leur graphe de dépendances le confirme.
- **Travaux** :
  - créer le module `domain-budget` ;
  - déplacer les classes retenues vers un package métier propre, par exemple `com.moe.myfamilybudget.domain.budget.*` ;
  - déplacer leurs tests unitaires associés ;
  - supprimer les dépendances non nécessaires vers Spring/JPA/API ;
  - faire dépendre les modules consommateurs de `domain-budget` plutôt que de l’ancien package `internal.model` ;
  - ne pas déplacer `BudgetDataModel` dans ce module ;
  - ne pas déplacer les modèles explicitement attachés à un domaine spécialisé sans vérifier leurs consommateurs.
- **Décisions retenues** :
  - module `back/domain-budget` (artifact `domain-budget`, jar, aucune dépendance : JDK uniquement), package `com.moe.myfamilybudget.domain.budget` ;
  - 12 classes déplacées, toutes des `record` publics sans dépendance : `IncomeModel`, `ChargeModel`, `OneOffExpenseModel`, `TransferModel`, `VariableIncomeModel`, `VariableOverrideModel`, `CashflowYearModel`, `TripleAmountModel`, `CategoryOptionModel`, `RealAverageModel`, `VariablePreviewModel`, `VariablePreviewCellModel` ;
  - aucun test unitaire dédié n'existait pour ces records : ils restent couverts par les tests des consommateurs dans `server` ;
  - `server` dépend de `domain-budget` (version gérée par `back/pom.xml`) ; `BudgetDataModel` reste dans `server` ;
  - les règles ArchUnit `PureLayerRules`, `DomainBoundaryRules` et `CalculationDependenciesArchTest` traitent `..domain.budget..` comme une couche pure au même titre que `internal.model` ;
  - `server` dépendant d'un module frère, `mvn -f back/server/pom.xml test` n'est plus autonome : le build passe par le reactor, `mvn -f back/pom.xml -B -pl server -am test` (CI, Dockerfile, gate VT-600, lanceur E2E et messages des scripts mis à jour). Le livrable reste `back/server/target/server-1.0.0-SNAPSHOT.jar`.
- **Critères de sortie** : `domain-budget` ne dépend d’aucun module de persistance, API ou application.
- **Tests** : tests unitaires du module ; compilation du reactor ; non-régression des domaines consommateurs.
- **Parallélisation** : peut avancer avec les premières extractions métier si les agents évitent les mêmes fichiers.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [x] Terminé

# MAVEN-020 — Extraire `domain-retirement`

- **Prérequis** : MAVEN-010.
- **Objectif** : transformer le domaine Retraite déjà découplé en véritable module Maven.
- **Contenu cible** :
  - `internal.calculation.RetirementCalculationInput`
  - `RetirementCalculationService`
  - `RetirementParameters`
  - `RetirementPersonInput`
  - `AnnualSalaryProjection`
  - `SalaryHistoryEntry`
  - `AnnualTaxableRetirementIncome`
  - `RetirementIncomeProjection`
  - `internal.model.RetirementModel`
  - `RetirementProjection`
  - `RetirementProjectionModel`
  - résultats métier Retraite nécessaires au domaine ;
  - `RetirementReader`, `RetirementWriter`, `RetirementSettingField` côté contrat propriétaire si leur responsabilité finale est bien Retraite.
- **À laisser hors module** : `RetirementInputFactory`, repositories JPA, `RetirementPersistenceAdapter`, contrôleurs, DTO OpenAPI.
- **Travaux** :
  - créer `domain-retirement` ;
  - réorganiser le package en sous-packages cohérents (`calculation`, `model`, `port`) si utile ;
  - faire en sorte que le moteur compile sans Spring/JPA ;
  - remplacer les imports des anciens packages dans l’application.
- **Décisions retenues** :
  - module `back/domain-retirement` (artifact `domain-retirement`, jar), sans dépendance de production : ni Spring, ni JPA, ni autre module du reactor (il ne dépend pas de `domain-budget`) ;
  - sous-packages `com.moe.myfamilybudget.domain.retirement.calculation` (8 classes : `RetirementCalculationInput`, `RetirementCalculationService`, `RetirementParameters`, `RetirementPersonInput`, `AnnualSalaryProjection`, `SalaryHistoryEntry`, `AnnualTaxableRetirementIncome`, `RetirementIncomeProjection`), `.model` (`RetirementModel`, `RetirementProjection`, `RetirementProjectionModel`) et `.port` (`RetirementReader`, `RetirementWriter`, `RetirementSettingField`) ;
  - `RetirementCalculationService` perd son `@Component` pour que le moteur compile sans Spring : son bean est déclaré dans `server` par `com.moe.myfamilybudget.config.DomainEngineConfig` (convention reprise par les extractions suivantes) ;
  - restent dans `server` : `RetirementInputFactory`, `RetraiteResultModel` et `RetraitePersonWithProjectionModel` (vues composites dépendant de `SettingsModel`/`IncomeModel`, relevant de la couche application), adapters/repositories JPA, mappers, contrôleurs ;
  - `RetirementCalculationServiceTest` est déplacé dans le module (JUnit 5 + AssertJ en dépendances de test) ; les tests transverses (`RetraiteServiceImplTest`, `RetirementToOverviewScenarioTest`, fixtures ArchUnit) restent dans `server` ;
  - les règles ArchUnit `PureLayerRules`, `DomainBoundaryRules` et `CalculationDependenciesArchTest` traitent `..domain.retirement..` comme couche pure ; la détection des domaines reste faite par nom de classe, donc inchangée ;
  - le gate VT-600 analyse aussi les sources de test du module.
- **Critères de sortie** : aucune dépendance vers `internal.persistence`, `api`, `PersistenceManager` ou `BudgetDataModel`.
- **Tests** : tests de composants Retraite ; règle ArchUnit ; test d’intégration Retraite → Overview inchangé.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [x] Terminé

# MAVEN-030 — Extraire `domain-tax`

- **Prérequis** : MAVEN-020.
- **Objectif** : isoler Fiscalité derrière les projections explicites de Retraite.
- **Contenu cible** :
  - `TaxCalculationInput`
  - `TaxCalculator`
  - `TaxBracket`
  - `TaxRateOverride`
  - `TaxActualOverride`
  - `TaxHouseholdParameters`
  - `TaxSimulationPeriod`
  - `AnnualTaxIncome`
  - `AnnualVariableIncome`
  - `AnnualTaxableRetirementIncome`
  - `TaxProjection`
  - modèles métier fiscaux (`TaxResultModel`, `TaxYearlyModel`, `TaxChildModel`, etc.) selon graphe réel ;
  - `TaxReader`, `TaxWriter`, `TaxSettingField`.
- **Dépendance autorisée** : contrat/projection Retraite, mais jamais l’implémentation `RetirementCalculationService`.
- **À laisser hors module** : `TaxInputFactory`, `TaxSimulationPeriodResolver`, JPA et application.
- **Travaux** : même méthode que MAVEN-020 ; déplacer tests avec leurs responsabilités.
- **Critère de sortie** : Fiscalité reste indépendante de la persistance et dépend uniquement des contrats autorisés.
- **Tests** : tests du calculateur ; ArchUnit ; scénarios `/impots` et Settings.
- **Statut** : [x] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

# MAVEN-040 — Extraire `domain-wealth`

- **Prérequis** : MAVEN-020.
- **Objectif** : isoler Patrimoine et ses moteurs déjà purifiés.
- **Contenu cible** :
  - `PatrimoineProjectionInput`
  - `PatrimoineProjectionParameters`
  - `PatrimoineProjectionService`
  - `PatrimoineProjection`
  - `PlacementProjectionInput`
  - `PlacementEvolutionInput`
  - `PlacementEvolutionService`
  - `PlacementEvolution`
  - `PlacementCashflowInput`
  - `PlacementHistoryPoint`
  - `PlacementTransfer`
  - `ContributionPauseRules`
  - `PauseState`
  - `RealEstateProjection`
  - modèles métier de patrimoine et immobilier réellement nécessaires aux contrats ;
  - `PatrimoineReader`, `PatrimoineWriter`, `PatrimoineList`, `AssetCategoryField`.
- **À laisser hors module** : `PatrimoineInputFactory`, `AssetBucketResolver` si utilisé aussi par Crédit, adapters JPA, mappers REST.
- **Règle** : aucun retour Patrimoine → Trésorerie.
- **Travaux** : extraire le module et remplacer les dépendances par les projections/records déjà créés.
- **Tests** : tests des deux moteurs patrimoniaux ; scénarios patrimoine → trésorerie → overview ; ArchUnit.
- **Statut** : [x] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

# MAVEN-050 — Extraire `domain-treasury`

- **Prérequis** : MAVEN-030, MAVEN-040.
- **Objectif** : matérialiser le domaine Trésorerie comme consommateur de projections, sans dépendance aux implémentations amont.
- **Contenu cible** :
  - `TreasuryProjectionInput`
  - `TreasuryProjection`
  - `TreasuryParameters`
  - `TreasurySimulationPeriod`
  - `IncomeProjectionInput`
  - `ChargeProjectionInput`
  - `VariableIncomeProjection`
  - `OneOffCashflow`
  - `TransferProjection`
  - `PlacementCashflowInput`
  - `TaxProjection`
  - `RetirementIncomeProjection`
  - `TresorerieCalculationService`
  - modèles de résultat Trésorerie ;
  - ports Trésorerie propriétaires.
- **Dépendances autorisées** : `domain-budget`, contrat Retraite, contrat Fiscalité, contrat Patrimoine si la composition retenue l’exige sans cycle.
- **Travaux** : déplacer le moteur et les contrats ; laisser `TreasuryInputFactory` côté application.
- **Critères de sortie** : aucune dépendance JPA/persistence ; aucun import de `internal.impl` ou `internal.factory`.
- **Tests** : composant Trésorerie ; scénario Overview ; tests de Settings et suggestions.
- **Statut** : [x] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

# MAVEN-060 — Extraire `domain-bank-pointage`

- **Prérequis** : MAVEN-010.
- **Objectif** : réunir dans un module bancaire les contrats et calculateurs Banque/Pointage, sans embarquer Enable Banking ni JPA.
- **Contenu cible** :
  - `BankImportModel` et modèles bancaires associés ;
  - `BankImportCalculator` ;
  - `PointageInput`, `PointagePeriod`, `PointageCalculator` ;
  - modèles de lecture Pointage nécessaires au domaine ;
  - `BankReader`, `BankWriter`.
- **À laisser hors module** : `EnableBanking*`, `BankPersistenceAdapter`, `BankImportDocumentMapper`, repositories, contrôleurs.
- **Travaux** :
  - décider et documenter la frontière exacte entre Banque et Pointage ;
  - éviter de déplacer `BankImportModel` dans plusieurs modules ;
  - préserver la règle « pas de modèle global artificiel ».
- **Tests** : calcul Banque, calcul Pointage, caractérisation des endpoints bancaires, ArchUnit.
- **Statut** : [x] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

# MAVEN-070 — Extraire `domain-analysis`

- **Prérequis** : MAVEN-010, MAVEN-060.
- **Objectif** : isoler Analyse comme consommateur de contrats bancaires et budgétaires.
- **Contenu cible** :
  - `AnalyseInput`
  - `AnalysisPeriod`
  - `MonthlyBudgetLines`
  - `BudgetLineKind`
  - `BudgetLineProjection`
  - `AnalyseCalculator`
  - `AnalyseResultModel`
  - modèles détaillés d’analyse (`AnalyseKpiModel`, `AnalyseDriftRowModel`, etc.).
- **Dépendances autorisées** : `domain-bank`, `domain-budget`.
- **À laisser hors module** : `AnalyseInputFactory`, `AnalyseServiceImpl`, mapper REST.
- **Travaux** : déplacer le calculateur et ses tests ; garantir l’absence de dépendance aux modèles persistants.
- **Tests** : tests de composant Analyse ; E2E Analyse ; ArchUnit.
- **Statut** : [x] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

# MAVEN-080 — Extraire `domain-credit` et `domain-goals`

- **Prérequis** : MAVEN-010, MAVEN-040.
- **Objectif** : isoler les domaines périphériques sans renforcer les dépendances croisées.
- **Contenu `domain-credit`** :
  - `LoanInput`
  - `LoanAdviceInput`
  - `LoanAdviceParameters`
  - `LoanAdviceCalculationService`
  - `PlacementRateSuggestionInput`
  - `PlacementRateSuggestionService`
  - modèles de prêts/suggestions nécessaires ;
  - `LoanReader`, `LoanWriter`.
- **Contenu `domain-goals`** :
  - `ObjectifsParameters`
  - `ObjectifReachableInput`
  - `PlacementBalanceSnapshot`
  - modèles Objectifs ;
  - `GoalReader`, `GoalWriter`.
- **Travaux** :
  - décider où vit définitivement `PlacementRateSuggestion*` si son ownership reste partagé avec Patrimoine ;
  - préserver l’abstraction `PlacementBalanceSnapshot` pour éviter `Goals → Wealth internals` ;
  - déplacer les tests unitaires avec les modules.
- **Tests** : tests Crédit/Suggestions ; tests Objectifs ; ArchUnit inter-domaines.
- **Statut** : [x] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

# MAVEN-090 — Extraire `domain-notifications`

- **Prérequis** : MAVEN-060, MAVEN-080.
- **Objectif** : faire de Notifications un consommateur final de contrats et non un domaine couplé au budget complet.
- **Contenu cible** :
  - `NotificationRule`
  - `NotificationMessage`
  - paramètres métier de notification ;
  - `rules/*` (`DebitThresholdRule`, `BalanceFloorRule`, `ObjectifReachableRule`) ;
  - contrats d’entrée correspondants.
- **À laisser hors module** : `NotificationDispatchService`, repositories de déduplication, `JpaNotificationSettingsStore`, Web Push.
- **Travaux** :
  - déplacer les règles pures ;
  - s’assurer que le module ne dépend d’aucun autre module de domaine sauf les contrats strictement nécessaires ;
  - laisser l’orchestration et la lecture des `Reader` dans `application`.
- **Tests** : tests des règles ; ArchUnit ; test du dispatch dans la couche application.
- **Statut** : [x] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

# MAVEN-100 — Extraire `application`

- **Prérequis** : MAVEN-020 à MAVEN-090.
- **Objectif** : regrouper l’orchestration applicative et les assemblers sans la confondre avec les domaines purs.
- **Contenu cible** :
  - `internal.command.*` : services de commandes et routage Settings ;
  - `internal.factory.*` : factories d’Input, résolveurs et assemblers applicatifs ;
  - `internal.impl.*` : services d’application/REST actuels après renommage éventuel en package `application.service` ;
  - `NotificationDispatchService` ;
  - éventuellement `BudgetFacadeView` si elle est destinée à rester un ViewModel applicatif.
- **Travaux** :
  - créer un package explicite `application`; éviter `internal.impl` comme frontière finale ;
  - supprimer les dépendances directes vers l’ancien monolithe de packages ;
  - faire dépendre l’application des Reader/Writer et contrats des domaines ;
  - conserver les opérations globales import/reset/export comme orchestration applicative ;
  - ne pas déplacer les controllers REST ici si le choix final prévoit un module `api/server-app` distinct.
- **Point critique** : les factories peuvent encore lire/assembler à partir de plusieurs domaines ; elles constituent la zone d’assemblage légitime.
- **Tests** : tests de services applicatifs, tests de commands, scénarios d’intégration.
- **Critère de sortie** : aucun domaine pur ne dépend de `application`.
- **Statut** : [x] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

# MAVEN-110 — Extraire `api` / OpenAPI généré

- **Prérequis** : MAVEN-100.
- **Objectif** : séparer le contrat REST généré du code applicatif et métier.
- **Contenu cible** :
  - sources générées `com.moe.myfamilybudget.api.*` ;
  - OpenAPI racine et fichiers `openapi/domains/*` ;
  - configuration de bundle Redocly/OpenAPI Generator ;
  - DTO/interfaces REST générés.
- **Travaux** :
  - créer un module `api` ;
  - choisir si ce module produit uniquement un jar de contrat ou également les interfaces Spring générées ;
  - conserver une seule génération des DTO partagés ;
  - supprimer le couplage des domaines à l’API ;
  - stabiliser le bundling OpenAPI indépendamment de l’application.
- **Attention** : ne pas mettre de logique de mapping métier dans `api`.
- **Tests** : génération OpenAPI ; compilation des mappers applicatifs ; vérification d’absence de dépendance inverse `api → domain` si le module est conçu comme contrat pur.
- **Statut** : [x] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

# MAVEN-120 — Extraire `persistence`

- **Prérequis** : MAVEN-100.
- **Objectif** : regrouper tout ce qui est infrastructure de persistance, même si son contenu continue à évoluer pendant les `DB-xxx`.
- **Contenu cible** :
  - `internal.persistence.*`
  - `entity/*`
  - `repository/*`
  - `adapter/*`
  - `converter/*`
  - `PersistenceManager`
  - `BudgetPersistenceGateway`
  - `BudgetCacheStore`
  - `BudgetMutationService`
  - `DomainMutations`
  - `BudgetMutatedEvent`
  - stores JPA des sous-domaines.
- **Travaux** :
  - créer le module `persistence` ;
  - rendre explicites les dépendances vers les domaines qu’il persiste ;
  - déplacer progressivement les repositories/entities sans imposer la fin de `DB-1100..1180` ;
  - conserver les adapters comme implémentations des Reader/Writer de leurs domaines ;
  - éviter que `persistence` expose des classes JPA au module application lorsqu’un port suffit.
- **Parallélisation** : peut avancer pendant `DB-xxx`, en particulier pendant le nettoyage des entités et converters.
- **Tests** : tous les tests JPA/repository/adapter ; PostgreSQL ; rollback ; concurrence ; redémarrage Spring.
- **Critère de sortie** : les domaines métier ne dépendent pas du module `persistence`.
- **Statut** : [x] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

# MAVEN-130 — Extraire `server-app` / composition root

- **Prérequis** : MAVEN-100, MAVEN-110, MAVEN-120.
- **Objectif** : faire du dernier module un runtime exécutable et un composition root Spring mince.
- **Contenu cible** :
  - `ServerApplication` ;
  - configuration runtime Spring ;
  - bootstrap/launcher ;
  - controllers REST d’implémentation lorsqu’ils ne doivent pas rester dans `api` ;
  - assemblage des beans application/persistence ;
  - `enablebanking` et `marketdata` si leur extraction en module séparé n’apporte pas de valeur à ce stade ;
  - `snapshot` et intégrations runtime.
- **Travaux** :
  - conserver un seul artifact exécutable final ;
  - configurer le plugin Spring Boot uniquement ici ;
  - déplacer hors de ce module les tests unitaires purs ;
  - conserver les tests d’intégration nécessitant le contexte complet ici ou dans une source de test dédiée.
- **Tests** : démarrage Spring ; endpoints critiques ; import/reset ; E2E ; packaging Docker.
- **Statut** : [x] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

# MAVEN-140 — Stabiliser le graphe et ajouter les garde-fous Maven

- **Prérequis** : MAVEN-100, MAVEN-110, MAVEN-120, MAVEN-130.
- **Objectif** : vérifier que le graphe Maven matérialise les règles d’architecture et ne repose plus uniquement sur ArchUnit.
- **Travaux** :
  - produire la matrice des dépendances entre modules ;
  - vérifier l’absence de cycles ;
  - supprimer les dépendances inutiles ;
  - mettre à jour les règles ArchUnit pour viser les nouveaux packages/modules ;
  - ajouter une règle ou un test empêchant l’import d’un module d’infrastructure depuis les domaines purs ;
  - vérifier que `BudgetDataModel` ne s’est pas réintroduit comme contrat inter-module ;
  - documenter le graphe dans `00-principes.md` ou un nouveau document dédié.
- **Critères de sortie** :
  - graphe acyclique ;
  - dépendances autorisées explicitement documentées ;
  - aucun cycle introduit par les services transverses ;
  - build complet vert.
- **Tests** : Maven reactor + ArchUnit + compilation complète.
- **Statut** : [x] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

# MAVEN-150 — CI sélective par sous-graphe Maven

- **Prérequis** : MAVEN-140.
- **Objectif** : réduire le travail CI lorsqu’une modification ne concerne qu’un sous-ensemble du serveur.
- **Principe** : le mono-repo reste la source de vérité ; la CI calcule le ou les modules impactés par le diff, puis construit leur fermeture de dépendances nécessaire.
- **Travaux** :
  - identifier les chemins Maven affectés à partir du diff Git ;
  - mapper chemins source/tests → module Maven ;
  - utiliser `mvn -pl <modules> -am test` lorsque le sous-graphe est suffisamment local ;
  - conserver un build complet sur `main` et sur les chemins globaux critiques (`pom.xml`, OpenAPI global, application, persistence, Docker, scripts de build) ;
  - séparer la boucle « tests domaine ciblé » de la boucle « tests d’intégration/E2E complets » ;
  - conserver PostgreSQL pour les tests qui en ont besoin ;
  - ne pas considérer le cache Maven comme un substitut à la sélection de modules.
- **Critère de sortie** : une modification d’un domaine isolé ne déclenche pas systématiquement tous les tests JVM unitaires indépendants.
- **Tests** : plusieurs PR artificielles ou jeux de chemins représentatifs ; contrôle qu’un changement transversal force correctement le build complet.
- **Statut** : [x] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

# MAVEN-160 — Revue de candidatures au multi-repo

- **Prérequis** : MAVEN-150 et `DB-1180` terminé.
- **Objectif** : décider quels modules, s’il en existe, justifient réellement un repository et un cycle de release indépendants.
- **Critères de décision** :
  - API publique suffisamment stable ;
  - très faible nombre de dépendances entrantes/sortantes ;
  - absence de cycle de release commun obligatoire avec `server` ;
  - intérêt réel à réutiliser le module hors de MyFamilyBudget ;
  - coût de versionnement et de publication inférieur au gain attendu ;
  - stratégie de publication disponible (par exemple GitHub Packages) ;
  - tests contractuels indépendants ;
  - documentation/versioning compatibles avec un artifact consommé à distance.
- **Travaux** :
  - produire la liste des modules candidats ;
  - distinguer bibliothèque réutilisable, module interne mono-repo et composant d’application ;
  - ne migrer en multi-repo que les candidats répondant aux critères ;
  - ne pas transformer tous les modules Maven en repositories par principe.
- **Livrable** : décision documentée module par module, avec dépendances et stratégie de versioning.
- **Statut** : [x] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

---

# Répartition cible initiale des packages

La correspondance ci-dessous est une **cible de migration**, pas une obligation de déplacer chaque classe exactement ainsi.

| Module Maven | Packages / contenus principaux | À exclure du module |
|---|---|---|
| `domain-budget` | modèles budget de base simples | `BudgetDataModel`, JPA, API |
| `domain-retirement` | calcul Retraite, projections, modèles Retraite, ports Retraite | factories, JPA, REST |
| `domain-tax` | calcul Fiscalité, projections, modèles fiscaux, ports Fiscalité | factories, JPA, REST |
| `domain-wealth` | calcul Patrimoine, projections, modèles patrimoine, ports Patrimoine | factories, JPA, REST |
| `domain-treasury` | calcul Trésorerie, projections, modèles trésorerie, ports Trésorerie | factories, JPA, REST |
| `domain-bank-pointage` | Banque/Pointage, calculateurs, modèles bancaires, ports Banque | Enable Banking, JPA, REST |
| `domain-analysis` | Analyse Input/calcul/résultats | factories, application, REST |
| `domain-credit` | calcul prêts/suggestions, modèles Crédit, ports Crédit | JPA, REST |
| `domain-goals` | calcul Objectifs, modèles Objectifs, ports Objectifs | accès direct Patrimoine, JPA |
| `domain-notifications` | règles et contrats Notification | dispatch, Web Push, JPA |
| `application` | `command`, `factory`, services `impl` renommés, orchestration, assemblers | entités JPA, DTO générés |
| `api` | OpenAPI + DTO/interfaces générés | logique métier |
| `persistence` | `persistence/*`, JPA entities/repositories/converters/adapters/stores | logique métier, controllers |
| `server-app` | `ServerApplication`, runtime Spring, controllers runtime, bootstrap, intégrations runtime | moteurs métier purs |

## Cas particuliers à traiter explicitement

### `internal.model`

Ce package transversal est une **zone de migration**, pas un futur module Maven.

Il devra être vidé domaine par domaine :

- les modèles budget simples → `domain-budget` ;
- les modèles Retraite → `domain-retirement` ;
- les modèles Fiscaux → `domain-tax` ;
- les modèles Patrimoine → `domain-wealth` ;
- les modèles Trésorerie → `domain-treasury` ;
- les modèles Banque/Pointage → `domain-bank-pointage` ;
- les modèles Analyse → `domain-analysis` ;
- les modèles Crédit → `domain-credit` ;
- les modèles Objectifs → `domain-goals` ;
- les modèles Notification → `domain-notifications` ;
- `BudgetDataModel` reste traité séparément comme snapshot global de transition.

### `internal.calculation`

Ce package est également une zone de migration. Les classes doivent être déplacées selon leur domaine métier, pas
vers un unique module `calculation`.

Cas volontairement différés hors domaines :

- `Jpa*Store` ;
- services d’accès Settings persistants ;
- composants nécessitant réellement une infrastructure externe.

### `internal.port`

Les ports doivent rejoindre le **module qui possède le contrat**, sauf si un contrat transversal de lecture impose une
bibliothèque d’abstractions distincte. Ne pas créer un énorme module `ports` uniquement pour reproduire le package historique.

### `internal.factory`

À déplacer dans `application`, car ces classes font l’assemblage de données entre domaines et Readers.

### `internal.mapper`

À répartir :

- mapping d’un résultat purement métier vers un DTO de domaine → application/API selon responsabilité ;
- `BudgetFacadeView` → application ;
- mapping strictement OpenAPI → application/API ;
- mapping JPA ↔ modèle → persistence.

### `internal.persistence`

À déplacer presque intégralement vers `persistence`.
Les `DB-xxx` peuvent continuer à modifier ce module pendant toute la phase de migration JPA.

### `internal.enablebanking` / `internal.marketdata`

Ne pas créer trop tôt deux modules Maven supplémentaires uniquement pour faire correspondre un package à un module.
Les laisser dans `server-app` ou dans un module d’infrastructure commun tant qu’un cycle de vie autonome n’est pas démontré.

---

# Stratégie de parallélisation

## Vague 1

Peuvent démarrer ensemble :

```text
MAVEN-000
   ↓
MAVEN-010

En parallèle côté DB : DB-xxx
```

Puis, dès `MAVEN-010` stabilisé :

```text
MAVEN-020 ─────┐
MAVEN-040 ─────┼── parallèles
MAVEN-060 ─────┘
```

## Vague 2

Après les premiers contrats :

```text
MAVEN-030
MAVEN-050
MAVEN-070
MAVEN-080
```

peuvent avancer avec une coordination minimale sur les contrats partagés.

## Vague 3

Une fois les domaines extraits :

```text
MAVEN-090
    ↓
MAVEN-100
    ├── MAVEN-110
    └── MAVEN-120
             ↓
         MAVEN-130
```

## Vague 4

```text
MAVEN-140
    ↓
MAVEN-150
    ↓
MAVEN-160   ← seulement après stabilisation finale
```

---

# Interaction avec les `DB-xxx`

Le principe est désormais :

```text
                ┌───────────────┐
                │ MAVEN-010..90 │
                │ domaines      │
                └───────┬───────┘
                        │
                        ▼
                 ┌─────────────┐
                 │ application │
                 └──────┬──────┘
                        │
                 ┌──────▼───────┐
                 │ persistence  │  ← DB-xxx continue ici
                 └──────────────┘
```

Les derniers nettoyages de `DB-1100` à `DB-1180` pourront donc continuer à supprimer progressivement le legacy de
persistance sans remettre en cause les modules de domaine déjà extraits.

`DB-1180` conserve toutefois une importance particulière pour la fin du chantier : il doit réduire `BudgetDataModel`
au snapshot global, ce qui permettra de vérifier que le dernier vestige du modèle monolithique ne constitue pas une
API inter-module accidentelle.

---

# Gate finale du chantier Maven

Avant de considérer la modularisation Maven comme acquise :

```bash
mvn -f back/server/pom.xml -B test
node tests/gate/check-gate.js all
bun run test
bun run test:e2e
```

et vérifier en plus :

- reactor Maven sans cycle ;
- dépendances inter-modules conformes au graphe documenté ;
- aucune dépendance d’un domaine pur vers `persistence`, JPA, OpenAPI ou Spring interdit ;
- `BudgetDataModel` absent des contrats de domaine ;
- `PersistenceManager` absent des domaines ;
- `api` sans logique métier ;
- `server-app` reste le seul module produisant le livrable Spring Boot exécutable ;
- les tests JPA importants s’exécutent contre PostgreSQL dans la CI ;
- la CI sélective ne masque aucun test transversal obligatoire ;
- Docker continue de produire le même livrable fonctionnel.

## Point de sortie

Lorsque `MAVEN-150` est vert, le projet dispose d’un **mono-repo modulaire avec build sélectif**.

`MAVEN-160` constitue ensuite un point de décision indépendant : seuls les modules réellement autonomes doivent être
candidats à une extraction en dépôts séparés et à une publication de JARs versionnés.
