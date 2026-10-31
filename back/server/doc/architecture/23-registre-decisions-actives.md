# 23 — Registre des décisions actives

Statut : 🟡 premier jet, à valider décision par décision (établi le 6 octobre 2026 à partir de `21-plan-silotage.md` et de
`22-etat-reel-main.md`)

## 1. Rôle et critère de tri

Ce registre ne garde que les décisions qui **contraignent encore du travail à faire**. Le récit de ce qui a été livré reste dans
`21-plan-silotage.md`, figé en archive.

Critère appliqué à chaque décision du document 21 : *un futur patch pourrait-il la violer sans que la CI le détecte ?*

- **oui, ou elle n'est pas encore appliquée** → section 3 (décisions actives) ;
- **non, elle est appliquée et verrouillée par un test ou par la structure du code** → section 4 (archivables), avec la preuve.

Colonne « Source » : référence dans le document 21. Colonne « Confirmation » : *décidée* = tranchée par Marco dans le document 21 ;
*consolidée* = règle déduite de livraisons répétées, **jamais formulée comme décision** : à confirmer ou à rejeter.

## 2. Cap (stable)

1. Silos isolés : aucun silo ne connaît un autre silo (contrats propres au consommateur).
2. Persistance dans chaque silo (entités, repositories, mappers dans `<silo>-core`) ; `infra-jpa` ne porte que la technique.
3. `application` est la seule transverse et ne connaît que des `*-api` ; elle ouvre les transactions via un port.
4. `application` ≠ serveur web : le web convertit les DTO, `application` ne renvoie plus de `ResponseEntity`.
5. `BudgetDataModel` disparaît ; seul un usage de transition borné par un patch de suppression déjà planifié est admis.

## 3. Décisions actives

| ID | Règle | Motif | Ce qu'elle contraint dans le reste à faire | Source | Confirmation |
|---|---|---|---|---|---|
| DA-01 | `module-info` sur les `*-api`, `api`, `application-*` et `web` ; aucun sur les `*-core` (visibilité tenue par le graphe Maven et ArchUnit). Extension (R-92, 31 oct. 2026) : `api` (contrat REST généré par openapi-generator) porte son propre `src/main/java/module-info.java` déclarant les modules nécessaires au contrat OpenAPI (`com.fasterxml.jackson.annotation`, `io.swagger.v3.oas.annotations`, `jakarta.annotation`, `jakarta.servlet`, `jakarta.validation`, `spring.context`, `spring.web`) et exportant `com.moe.myfamilybudget.api.controller` et `api.model` ; `jakarta.servlet:jakarta.servlet-api` est déclaré en dépendance dans `api` et `web` pour fournir le module JPMS `jakarta.servlet` à javac lors de la compilation modulaire intra-réacteur (résolution via `target/classes`) | pas de `opens` à maintenir pour Hibernate et Spring | création de `web`, `bootstrap`, `application-core` ; `module-info` de `application` et `web` (SILO-340) | D3, option A | décidée |
| DA-08 | Forme d'un lot B : `Jpa<Silo>Store` dans `<silo>-core` (implémente Reader, Writer, SnapshotWriter), écriture directe dans les tables du silo sous `TransactionRunner` et `SiloMutationLock` du silo, **sans `@Transactional` ni verrou dans le store** ; retrait de l'adaptateur de `persistence` et de la synchronisation du silo ; publication d'un événement `<Silo>MutatedEvent` après chaque écriture, écouté par `NotificationBudgetMutationListener` (« toute modification déclenche un contrôle ») ; tests round-trip, concurrence et redémarrage (lot B2) | gabarit commun aux pilotes Objectifs et Banque | tous les lots B restants ; **oublier l'événement n'est détecté par aucun test global** : les notifications cesseraient de se déclencher pour le silo | SILO-212 B1/B2, SILO-213 B/B2 | décidée (6 oct. 2026) |
| DA-09 | Toute écriture qui réécrit une collection entière d'un silo **lit, modifie et écrit sous le verrou du silo**, dans la transaction, par une fonction de modification (gabarit `BankImportCommandService.modifyBankImport`) | lire hors verrou puis réécrire l'ensemble perd la modification concurrente (« dernière écriture gagnante ») ; risque réel pour Banque à cause de la synchronisation en arrière-plan | à examiner au démarrage de **chaque** lot B : les services des autres silos n'ont pas été recensés (voir `22`, § 6) | SILO-213 « Décision » du 6 oct. 2026, option 2 | décidée (option 2 maintenue pour tous les silos, 6 oct. 2026) |
| DA-10 | **Intégrité inter-silos, phase 1 (retenue)** : le serveur reste tolérant (aucun contrôle ajouté, voir DA-11) ; la vérification « supprimer ce placement modifiera les objectifs O1, O2… », la confirmation de l'opérateur et le retrait de la référence dans chaque objectif concerné sont portés par le **front**. La dette est tracée (DT-01). **Phase 2 (chantier isolé, plus tard)** : portage côté serveur, règle étendue aux autres thèmes fonctionnels. En migration de données, une référence vers un placement supprimé est simplement vidée | trois trous d'intégrité tolérés aujourd'hui (allocation orpheline, `categoryId` orphelin, virement orphelin) ; évite un changement de contrat REST dans la phase de séparation | SILO-240 lot B2 devient « pas de code serveur » en phase 1 ; un patch front est à ajouter au backlog ; aucune règle serveur ne doit supposer l'absence d'orphelins | SILO-240 « Décision retenue », aménagée le 6 oct. 2026 | décidée |
| DA-11 | La seule règle d'intégrité inter-silos aujourd'hui appliquée est la sur-allocation Objectifs → Patrimoine (message et HTTP 400 inchangés) ; les références budget → placements sont **tolérantes en lecture** et doivent le rester (`budgetLineId` inconnu retombe sur `charge`) | comportement historique à conserver | périmètre de DA-10 : ne pas rendre ces lectures strictes par accident | SILO-240 lot A | décidée |
| DA-13 | Méthode de patch : additif, bascule, suppression ; patch petit, mergeable seul, **sans changement de contrat REST** ; un patch qui rencontre un arbitrage non tranché devient « bloqué » et s'arrête ; la CI arbitre (Maven indisponible côté agent) ; patchs livrés en LF | héritée de `18` et du fonctionnement avec les agents | tous les patchs restants | § 8 d'ouverture, risque 6 | décidée |
| DA-14 | **Les virements appartiennent à Trésorerie** (table `cashflow_transfer`) ; Patrimoine ne les lit ni ne les écrit directement : l'application les lui fournit | aujourd'hui `PatrimoinePersistenceAdapter` lit `CashflowTransferRepository` (cœur de Trésorerie) et son `replace` réécrit les virements ; deux silos écrivent la même table | lots B de Trésorerie et de Patrimoine ; retrait de `TransferEntity` du hub | note DB-1061 du code, SILO-131 | décidée (6 oct. 2026) |
| DA-15 | **Le PASS (`pass2026`, `passGrowthRate`) appartient à Retraite** et reste dans `pension_plan` (`pass_2026`, `pass_growth_rate`), où il est déjà lu et écrit par `RetirementModel` ; les deux colonnes homonymes de `SettingsEntity` sont des reliquats : aucune donnée n'y est ajoutée, elles disparaissent avec `SettingsEntity` (lot B2 de SILO-220). Elles ne vont **pas** dans `pension_settings` (doublon) | `SettingsModel` ne les porte plus depuis SET-040 ; seul `OverviewMapper.withLegacyPassFallback` relit un ancien import JSON | script Liquibase (DA-03) : copier la valeur de `settings` vers `pension_plan` là où celle-ci est vide, puis supprimer les colonnes ; le repli de l'import JSON reste côté DTO, à retirer quand les anciens exports ne servent plus | SET-040, `22` § 2 | décidée (6 oct. 2026) |
| DA-16 | **R-01 lot B, « reset propre »** : la baseline Liquibase n'est pas du reverse-engineering de l'existant (H2 dev et PostgreSQL de prod ont divergé : 4 tables orphelines propres à H2 — `retirement`, `retirement_people`, `retirement_person`, `salary_history` —, 2 propres à PostgreSQL — `objectif`, `objectif_allocation` —, et 14 communes aux deux, anciens noms pré-renommage de silo : `asset_category`, `bank_import`, `charge`, `income`, `loan`, `one_off_expense`, `real_estate`, `tax_actual_override`, `tax_bracket`, `tax_child`, `tax_rate_override`, `transfer`, `variable_income`, `variable_override`). La baseline ne retient que les tables ayant encore une entité JPA aujourd'hui (36 tables, correspondance 1:1 avec les 11 silos, zéro FK inter-silo). Déploiement : export JSON (`/budget`) + `pg_dump` de sauvegarde, base PostgreSQL de prod vidée, redémarrage (Liquibase recrée le schéma propre depuis la baseline), réimport JSON — l'import passe déjà par les writers par silo (SILO-119 lot B2), aucune dépendance aux tables orphelines. `LegacySchemaCleanup` est retiré dans le même patch que la bascule : un schéma neuf n'a plus de FK obsolète à nettoyer | H2 et PostgreSQL ont des historiques de migration différents ; générer depuis l'existant aurait figé cette dette dans la baseline au lieu de la purger ; `LegacySchemaCleanup` dépend du métamodèle JPA (`EntityManagerFactory`), indisponible au moment où Liquibase s'exécute — un portage littéral en `CustomTaskChange` n'aurait pas pu reproduire son comportement dynamique | toute table encore legacy mais pas encore migrée vers un silo (aucune actuellement : `persistence-legacy.changelog.xml` ne porte plus que `budget_data`/`settings`/`placement`/`placement_history_entry`, toutes avec entité JPA) reste listée explicitement, jamais déduite d'un dump ; un futur reset du même type doit repartir de la liste des `@Table` du code, pas d'un `generateChangeLog` brut | 24-backlog-reste-a-faire.md (R-01), 25-cartographie-modules-patchs.md, échange agent du 20 oct. 2026 | décidée |

## 4. Archivables : appliquées et verrouillées

À valider avant de les sortir du document 21 : une ligne qu'on ne reconnaît pas reste en section 3.

| Décision | Preuve de l'application | Ce qui la verrouille |
|---|---|---|
| D1 : pas de type partagé entre silos, chaque consommateur définit ses entrées | SILO-130 à SILO-134 livrés ; `MavenModuleGraphExceptions` vide | `MavenModuleGraphTest`, règle A (un silo ne dépend d'aucun autre silo) |
| D2 : dissolution de `domain-budget`, pas de module `common` | module supprimé (SILO-140) ; `TripleAmountModel` et `RealAverageModel` dans `treasury-api` | absence du module dans `back/pom.xml` |
| D6 : import/export/reset par fragments, format `BudgetDataDto` inchangé | `GlobalBudgetSnapshotService` sans `BudgetDataModel` (SILO-119 B2) ; `GlobalBudgetSnapshotFragmentsTest` | `ApplicationAndServerWithoutBudgetDataModelArchTest`, test d'ordre verrou → silos |
| D7 : Marché = silo, Enable Banking = intégration du silo Banque | `market-api`/`market-core` ; `DefaultEnableBankingSyncService` dans `bank-pointage-core` | `ApplicationWithoutSiloCoreArchTest` (règle générique sur `domain.*.core..`) |
| Règle de sur-allocation portée dans `application` | `GoalAllocationRule`, `GoalCommandService` | `InterSiloIntegrityCharacterizationTest`, `GoalAllocationRuleTest` |
| Pilote Objectifs puis Banque (lots A, B, B2) | `JpaGoalStore`, `JpaBankStore`, `GoalConcurrentMutationsApiTest`, `BankConcurrentMutationsApiTest` | tests de concurrence et `RestartPersistenceTest` ; les gabarits restent actifs en DA-08 et DA-09 |
| Verrou par silo ordonné, port `TransactionRunner` | `SiloMutationLockRegistry`, `SpringTransactionRunner` | `SiloMutationLockRegistryTest`, `ApplicationWithoutSpringTransactionArchTest` |
| `application` ne dépend que des `*-api` | `application/pom.xml` sans `*-core` | `MavenModuleGraphTest` règle C, `ApplicationWithoutSiloCoreArchTest`, `check-gate.js gate-a` |
| Silos sans moteur (Objectifs) : pas de `*-core` vide tant qu'il n'a pas de persistance | `goals-core` créé avec la persistance (SILO-212 A) | structure du reactor |
| `application-api` sans dépendance vers Spring, JPA, OpenAPI ou un silo | `ApplicationApiIndependenceArchTest` | test d'architecture |
| DA-04 : `LegacyObjectifAllocationMigrator` supprimé | module `persistence` (qui le portait) supprimé en bloc, R-61-24 | absence du fichier et du module dans `back/pom.xml` ; `26-sequencage-r61-suppression-hub.md` |
| DA-12 : entorses de transition bornées (`SettingsModel`, `SettingsReader`, `SettingsModelAssembler`, `BudgetDataModelAllowList`) | les quatre chemins de sortie annoncés ont tous été empruntés : `SettingsReader`/`SettingsModelAssembler` retirés des 12 fichiers d'`application` (R-52, SILO-100/120), module `transition-snapshot` (`SettingsModel`) supprimé (R-61-23), `BudgetDataModelAllowList` supprimée (R-61-25) | absence des fichiers et du module dans `back/pom.xml` ; `26-sequencage-r61-suppression-hub.md` (découpage complet R-61-01 à R-61-27) |
| DA-02 : une seule base, tables préfixées par silo, aucune clé étrangère entre silos | `SiloTablePrefixArchTest` (R-70, 32 entités JPA des 10 silos à persistance propre, 0 écart, 0 collision) ; `SiloPersistenceIsolationArchTest` (SILO-21x) | les deux tests d'architecture, exécutés à chaque build (R-71, Porte B) |
| DA-03 : migrations de schéma par Liquibase | baseline initiale (R-01 lot B), copie du PASS vers `pension_plan` puis suppression des 15 colonnes de `SettingsEntity` (R-51), suppression des 4 tables legacy du hub (R-61-26) : chaque changement de schéma depuis le 6 oct. 2026 a emporté son script | `ddl-auto: validate` actif (R-01 lot B) : tout schéma et entités JPA divergents font échouer le démarrage du contexte Spring, détecté par les tests `@SpringBootTest` de la CI (R-71, Porte B) |
| DA-05 : chaque lot B de silo emporte ses paramètres (`pension_settings`, `fiscal_settings`, `cashflow_settings`, `app_settings`) | lots B Retraite, Fiscalité, Trésorerie puis SILO-220 B2 (R-51, R-52) | `SettingsEntity` et `syncSettings` supprimés avec le hub (R-61) : plus aucun code ne peut les référencer |
| DA-06 : `simulateUntilAge`/`inflationRate` dans le silo Paramètres (`settings-api`/`settings-core`, `app_settings`) | `JpaAppSettingsStore` en production | `SettingsPersistenceAdapter` (implémentation sur le cache du hub) supprimé avec le hub (R-61) ; `JpaAppSettingsStoreTest`, `PersistenceAdaptersJpaRoundTripTest` |
| DA-07 : sortie du cache global sans big bang, lot B de SILO-206 en dernier | `GlobalCacheLockRelay` et `BudgetCacheStore.mutationLock` retirés (R-60) | classes supprimées (R-60) ; module `persistence`, qui portait `BudgetCacheStore`, supprimé en bloc (R-61-24) |

## 4 bis. Dettes tracées

| ID | Dette | Origine | À traiter par | Risque tant qu'elle existe |
|---|---|---|---|---|
| DT-01 | Le serveur ne vérifie pas l'impact de la suppression d'un placement référencé (objectifs, virements, catégories) ; la vérification et le retrait des références sont portés par le front | DA-10, phase 1 | petit chantier isolé (phase 2), après la Porte B | le front fait deux appels non atomiques (suppression, puis mise à jour de chaque objectif) : un échec intermédiaire laisse une allocation orpheline ; un import JSON ou un autre client contourne la vérification |

## 5. Points encore ouverts

1. **DA-14** : confirmer que les virements appartiennent à Trésorerie (le code et DA-07 en dépendent).
2. **Propriétaire des suggestions de taux** (`PlacementRateSuggestion*`, restées dans `server`) : Marché, Crédit ou Patrimoine ; aucune décision ne le dit (voir `24`, § 4).

Point résolu (R-71, Porte B) : **DA-03, forme de Liquibase** — fichier maître dans le composition root (`server`,
`db.changelog-master.xml`) qui inclut un changelog par silo dans chaque `*-core` ; `ddl-auto: validate` actif depuis
R-01 lot B. Retiré de cette liste.
