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
| DA-01 | `module-info` sur les `*-api`, `application-*` et `web` ; aucun sur les `*-core` (visibilité tenue par le graphe Maven et ArchUnit) | pas de `opens` à maintenir pour Hibernate et Spring | création de `web`, `bootstrap`, `application-core` ; `module-info` de `application` (SILO-340) | D3, option A | décidée |
| DA-02 | Une seule base, tables préfixées par silo, **aucune clé étrangère entre silos** ; les liens inter-silos sont des identifiants, l'intégrité est applicative | rend possible une transaction multi-silos avec un seul `PlatformTransactionManager` ; limite assumée : deux bases physiques supprimeraient l'atomicité gratuite | tous les lots B (tables propres), SILO-250 ; **le préfixe n'est vérifié par aucun test** (des tables du hub ne sont pas préfixées) | D4, § 4.2 | décidée |
| DA-03 | Les migrations de schéma passent par **Liquibase** | remplacer `ddl-auto: update` et la procédure manuelle (export JSON, suppression du schéma, réimport) | non appliquée : toute suppression de table (hub, `SettingsEntity`) impose aujourd'hui la procédure manuelle ; à regrouper pour limiter les interventions | D5, risque 9 | décidée ; **date et portée non décidées** |
| DA-04 | `LegacyObjectifAllocationMigrator` est supprimé (sinon déplacé dans le silo Objectifs) | aucun objectif réel en base à l'introduction de la migration | non appliquée : toujours appelé au chargement du cache ; `GoalAllocationRule` garde une parité de lecture « à retirer avec lui » | D8 | décidée |
| DA-05 | **Chaque lot B de silo emporte ses paramètres** : il les écrit directement dans sa table (`pension_settings`, `fiscal_settings`, `cashflow_settings`, `app_settings`), sous `SiloMutationLock`, les lit par un reader du silo et retire sa part de `syncSettings`. Ordre : Retraite, Fiscalité, Trésorerie, puis le lot B2 de SILO-220, qui ne garde que le retrait de `SettingsEntity` et de `SettingsPersistenceAdapter` | casse la dépendance circulaire entre les lots B de SILO-210/211 et SILO-220 | lots B de Retraite, Fiscalité, Trésorerie ; SILO-220 B2 ; retrait des 15 colonnes de `SettingsEntity` | D9, option A (6 oct. 2026) | décidée |
| DA-06 | `simulateUntilAge` et `inflationRate` sont des propriétés de l'application, pas de Trésorerie : silo **Paramètres** (`settings-api`, `settings-core`, table `app_settings`) | ces deux valeurs n'appartiennent à aucun silo métier | lecture et écriture des deux paramètres à basculer sur `app_settings` (les ports `SimulationSettings*` et `EconomicAssumptions*` sont déjà dans `settings-api`, mais implémentés par `SettingsPersistenceAdapter` sur le cache) | décision du 6 oct. 2026 (SILO-220 A2) | décidée |
| DA-07 | Sortie du cache global **sans big bang** : pilote (Objectifs, fait), généralisation silo par silo, **lot B de SILO-206 en dernier** (retrait de `GlobalCacheLockRelay` et de `BudgetCacheStore.mutationLock`, quand plus aucune écriture ne passe par le modèle complet). Chaque lot B est livré seul, CI verte avant le suivant | évite la dépendance circulaire entre SILO-206 et les lots B | séquencement de tout le reste à faire de la phase B | arbitrage du 5 oct. 2026 (SILO-206) | décidée ; l'ordre « Crédit, Patrimoine, Trésorerie » reste à fixer entre eux |
| DA-08 | Forme d'un lot B : `Jpa<Silo>Store` dans `<silo>-core` (implémente Reader, Writer, SnapshotWriter), écriture directe dans les tables du silo sous `TransactionRunner` et `SiloMutationLock` du silo, **sans `@Transactional` ni verrou dans le store** ; retrait de l'adaptateur de `persistence` et de la synchronisation du silo ; publication d'un événement `<Silo>MutatedEvent` après chaque écriture, écouté par `NotificationBudgetMutationListener` (« toute modification déclenche un contrôle ») ; tests round-trip, concurrence et redémarrage (lot B2) | gabarit commun aux pilotes Objectifs et Banque | tous les lots B restants ; **oublier l'événement n'est détecté par aucun test global** : les notifications cesseraient de se déclencher pour le silo | SILO-212 B1/B2, SILO-213 B/B2 | **consolidée** |
| DA-09 | Toute écriture qui réécrit une collection entière d'un silo **lit, modifie et écrit sous le verrou du silo**, dans la transaction, par une fonction de modification (gabarit `BankImportCommandService.modifyBankImport`) | lire hors verrou puis réécrire l'ensemble perd la modification concurrente (« dernière écriture gagnante ») ; risque réel pour Banque à cause de la synchronisation en arrière-plan | à examiner au démarrage de **chaque** lot B : les services des autres silos n'ont pas été recensés (voir `22`, § 6) | SILO-213 « Décision » du 6 oct. 2026, option 2 | décidée pour Banque ; **consolidée** pour les autres silos |
| DA-10 | **Intégrité inter-silos** : (a) en migration de données, une référence vers un placement supprimé est simplement vidée ; (b) en fonctionnement nominal, supprimer un placement référencé impose de **prévenir l'opérateur** (« La suppression de ce placement modifiera les objectifs O1, O2… »), d'exécuter la suppression **après confirmation**, et de retirer alors la référence de chaque objectif concerné. La règle s'applique aussi « aux autres thèmes fonctionnels » | trois trous d'intégrité tolérés aujourd'hui (allocation orpheline, `categoryId` orphelin, virement orphelin) | SILO-240 lot B2 ; touche l'API et le front (confirmation) | SILO-240 « Décision retenue » | décidée ; **tension avec la règle de contrat REST inchangé, voir § 5** |
| DA-11 | La seule règle d'intégrité inter-silos aujourd'hui appliquée est la sur-allocation Objectifs → Patrimoine (message et HTTP 400 inchangés) ; les références budget → placements sont **tolérantes en lecture** et doivent le rester (`budgetLineId` inconnu retombe sur `charge`) | comportement historique à conserver | périmètre de DA-10 : ne pas rendre ces lectures strictes par accident | SILO-240 lot A | décidée |
| DA-12 | Entorses de transition **bornées** : `SettingsModel` reste dans `transition-snapshot` jusqu'à SILO-230 ; `SettingsReader` et `SettingsModelAssembler` portent le bloc `settings` des réponses REST jusqu'à leur retrait ; la liste fermée de `BudgetDataModelAllowList` ne peut que décroître | garder un chemin de sortie explicite pour chaque exception | retrait de `SettingsReader` (12 fichiers), `SettingsModelAssembler`, `transition-snapshot` | SILO-002, SILO-100, SILO-120 | décidée |
| DA-13 | Méthode de patch : additif, bascule, suppression ; patch petit, mergeable seul, **sans changement de contrat REST** ; un patch qui rencontre un arbitrage non tranché devient « bloqué » et s'arrête ; la CI arbitre (Maven indisponible côté agent) ; patchs livrés en LF | héritée de `18` et du fonctionnement avec les agents | tous les patchs restants | § 8 d'ouverture, risque 6 | décidée |

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

## 5. Points à trancher avant de bâtir le backlog

1. **DA-10 contre DA-13.** La confirmation avant suppression d'un placement référencé change le contrat de l'API
   (réponse à préciser, ou second appel) et le front ; DA-13 impose un contrat REST inchangé. À arbitrer : soit un patch
   explicitement marqué « change le contrat », soit le comportement actuel (suppression silencieuse) jusqu'à ce patch.
2. **DA-03** : Liquibase est décidé mais ni daté ni borné. Tant qu'il ne l'est pas, chaque lot qui supprime une table
   (retraits du hub, `SettingsEntity`) suppose la procédure manuelle ; il faut choisir entre les regrouper en une seule
   intervention ou introduire Liquibase avant.
3. **DA-07** : l'ordre entre Crédit, Patrimoine et Trésorerie n'est pas fixé (DA-05 fixe seulement Trésorerie avant SILO-220 B2).
4. **DA-08 et DA-09** : si elles sont confirmées, elles deviennent des critères d'acceptation de chaque lot B (événement
   publié, lecture sous verrou). Sinon, elles retombent en pratique d'agent et ne sont plus opposables.
5. **Colonnes `pass2026` et `passGrowthRate` de `SettingsEntity`** : leur sort n'est dans aucune décision (voir `22`, § 2).
