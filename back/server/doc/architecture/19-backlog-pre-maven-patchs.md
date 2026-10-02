# 19 — Backlog de patchs de finition avant reprise de la persistance et avant modules Maven

Statut : 🟡 à exécuter depuis `main` courant

HEAD de référence audité lors de la rédaction : `95780018ea97e562e3ba59d277fbd1c049887990`
(1er octobre 2026).

## Objet

Ce fichier décrit les travaux **hors migrations JPA `DB-xxx`** qui restent à traiter avant de considérer les
frontières comme suffisamment propres pour poursuivre sereinement la séparation de la persistance puis
ouvrir le chantier de création des modules Maven.

Il corrige une ambiguïté du suivi précédent :

- `VT-330` est **terminé** ;
- `VT-340` est **terminé** ;
- les deux patchs ont néanmoins laissé derrière eux des constats explicitement hors périmètre ;
- ces constats deviennent ici des patchs `FIX-*` distincts. Il ne faut pas rouvrir les VT correspondants.

La CI du HEAD courant est verte : backend Maven, gate VT-600, smoke JS, Playwright et build Docker passent.
La variante PostgreSQL du test de redémarrage est exécutée en CI.

## Lecture obligatoire

Avant tout patch :

1. [`00-principes.md`](00-principes.md)
2. [`01-sequencement.md`](01-sequencement.md)
3. [`14-checklist-maven.md`](14-checklist-maven.md)
4. [`16-tests.md`](16-tests.md)
5. [`17-backlog-tests-patchs.md`](17-backlog-tests-patchs.md)
6. [`18-backlog-persistance-patchs.md`](18-backlog-persistance-patchs.md)
7. le document de domaine concerné le cas échéant.

## Règles de livraison

- Un patch = un item ci-dessous.
- Chaque patch doit compiler et laisser la CI verte.
- Aucun patch ne doit embarquer une migration JPA `DB-xxx` sauf si elle est indispensable à son propre test.
- Les patchs peuvent être développés en parallèle lorsqu'ils ne touchent pas les mêmes fichiers de centre de gravité.
- Ne pas corriger silencieusement un autre problème découvert pendant un patch : soit il est nécessaire au
  patch, soit il devient un nouvel item.
- Les tests E2E existants restent des tests fonctionnels : aucune assertion importante ne doit reposer uniquement
  sur le cache JS lorsque le backend peut être relu.
- Les règles ArchUnit doivent protéger les frontières sans interdire les assemblers applicatifs légitimes.
- Aucun nouveau contrat local ne doit prendre `BudgetDataModel` comme solution de facilité.

---

## Vue d'ensemble

| ID | Titre | Prérequis | Parallélisable avec | Bloquant Maven |
|---|---|---|---|---|
| FIX-010 | BankImport — propager les erreurs de persistance | aucun | FIX-020, RES-010, NOTIF-010, SET-010 | Oui |
| FIX-020 | `updateTaxSettings` — ne plus dépendre implicitement de `sweepEnabled` | aucun | FIX-010, RES-010, NOTIF-010, SET-010 | Oui |
| RES-010 | Results/Mappers — supprimer les dernières fuites fonctionnelles de `BudgetDataModel` | aucun | FIX-010, FIX-020, NOTIF-010, SET-010 | Oui |
| NOTIF-010 | Notifications — remplacer `PersistenceManager` par des ports de lecture | aucun | FIX-010, FIX-020, RES-010, SET-010 | Oui |
| SET-010 | Settings — définir l'ownership applicatif par domaine | aucun | FIX-010, FIX-020, RES-010, NOTIF-010 | Oui |
| SET-020 | Settings — brancher chaque famille sur son owner | SET-010, FIX-020 | RES-010, NOTIF-010 | Oui |
| SET-030 | Settings — supprimer le dispatcher générique `updateTaxSettings(field,value)` | SET-020 | ARCH-010, ARCH-020 | Oui |
| SET-040 | Settings — trancher et appliquer la duplication `pass2026/passGrowthRate` | SET-010 | RES-010 | Oui |
| ARCH-010 | ArchUnit — interdictions explicites par couche pure | RES-010, NOTIF-010 | ARCH-020 | Oui |
| ARCH-020 | ArchUnit — interdictions de dépendances inter-domaines | SET-030, ARCH-010 | aucun | Oui |
| CLEAN-010 | `BudgetDataModel` — inventorier puis réduire les usages résiduels | RES-010, SET-040 | ARCH-010 | Oui |
| CLEAN-020 | Snapshot global — isoler explicitement import/export/backup/reset | CLEAN-010 | ARCH-020 | Oui |
| DOC-010 | Réconcilier les statuts des backlogs/checklist | FIX-010, FIX-020, SET-040, ARCH-020 | aucun | Oui |
| GATE-010 | Gate final avant reprise DB / Maven | tout le reste | aucun | Oui |

---

# FIX-010 — BankImport : propager les erreurs de persistance

- **Prérequis** : aucun.
- **Finalité** : restaurer le contrat général « une écriture DB en échec ne devient pas une réussite en mémoire ».
- **Analyse actuelle** : `BudgetPersistenceGateway.saveBankImport` intercepte actuellement les exceptions et les journalise.
  `VT-330` a délibérément laissé ce cas hors périmètre.
- **Travaux attendus** :
  - ne plus absorber silencieusement l'exception ;
  - préserver l'exception d'origine ou la propager via une exception technique explicite ;
  - vérifier que `BudgetCacheStore` ne publie pas de nouvel état mémoire quand l'écriture BankImport échoue ;
  - ajouter/adapter le test dédié.
- **Tests** : test unitaire de défaillance ; contrôle qu'aucun `BudgetMutatedEvent` n'est publié ; contrôle des lecteurs après échec.
- **Limite** : ne pas refactorer ici la représentation JSON de `BankImportEntity`.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [x] Terminé

---

# FIX-020 — `updateTaxSettings` : supprimer la dépendance implicite à `sweepEnabled`

- **Prérequis** : aucun.
- **Finalité** : permettre à un dataset minimal de modifier un paramètre sans dépendre d'un champ d'un autre sous-domaine.
- **Analyse actuelle** : `BudgetMutationService.updateTaxSettings` peut lever une `NullPointerException` lorsque
  `sweepEnabled` n'est pas renseigné. `VT-340` contourne volontairement le problème dans son dataset.
- **Travaux attendus** :
  - remplacer l'accès non défensif par une valeur effective/défaut clairement définie ;
  - ne pas faire de la présence de `sweepEnabled` une précondition de modification d'un paramètre fiscal ou générique ;
  - ajouter un test sur budget minimal sans `sweepEnabled` ;
  - retirer progressivement le contournement des fixtures VT-230 / VT-340.
- **Tests** : test unitaire de mutation ; scénario Settings backend ; rerun E2E Settings.
- **Limite** : ne pas profiter de ce patch pour redéfinir l'ownership final des paramètres ; cela relève de `SET-*`.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [x] Terminé

---

# RES-010 — Results/Mappers : supprimer les dernières fuites fonctionnelles de `BudgetDataModel`

- **Prérequis** : aucun.
- **Finalité** : faire du `BudgetDataModel` un snapshot global et non une dépendance implicite des résultats métier/API.
- **Analyse actuelle** :
  - `AnalyseResultModel` et `OverviewResultModel` ont déjà été débarrassés du champ métier global ;
  - leurs mappers reçoivent encore un `BudgetDataModel` pour reconstruire certaines données API ;
  - plusieurs services applicatifs reconstruisent encore des snapshots globaux.
- **Travaux attendus** :
  - distinguer explicitement « assembler global pour une façade API » et « résultat métier » ;
  - faire passer aux mappers uniquement les données API nécessaires, ou un ViewModel de façade explicitement dédié ;
  - supprimer les signatures de mapper où `BudgetDataModel` n'est utilisé que comme raccourci ;
  - préserver les contrats REST actuels sauf évolution explicitement décidée.
- **Périmètre prioritaire** : `OverviewServiceImpl` / `OverviewMapper`, puis `AnalyseServiceImpl` / `AnalyseMapper`, puis les
  autres services lorsque le snapshot n'est plus indispensable.
- **Tests** : tests de mapping isolés ; tests de composants ; scénarios backend et E2E de référence.
- **Critère de sortie** : aucun `ResultModel` métier n'embarque `BudgetDataModel`.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [x] Terminé
- **Livraison** : `BudgetFacadeView` (package `internal.mapper`) devient la vue de façade des réponses composites
  `/overview` et `/analyse` ; `OverviewMapper.toDto(OverviewResultModel, BudgetFacadeView)` et
  `AnalyseMapper.toDto(AnalyseResultModel, BudgetFacadeView)` ne reçoivent plus `BudgetDataModel`. Contrats REST inchangés.
  Règles ArchUnit `ResultModelsArchTest`. Restent hors périmètre : `PatrimoineMapper`, `StatementBankImportMapper`,
  `TresorerieServiceImpl` (signatures publiques) et les services qui recomposent encore un snapshot pour les input
  factories (voir ARCH-010 / CLEAN-010).

---

# NOTIF-010 — Notifications : remplacer `PersistenceManager` par des ports de lecture

- **Prérequis** : aucun.
- **Finalité** : fermer la dernière dépendance directe notable de `PersistenceManager` dans cette couche d'application.
- **Analyse actuelle** : `NotificationDispatchService` récupère encore un `BudgetDataModel` complet via `PersistenceManager`,
  puis appelle `NotificationInputFactory`. Les règles elles-mêmes sont déjà correctement découplées.
- **Travaux attendus** :
  - identifier les lectures réellement nécessaires aux trois règles ;
  - utiliser les `Reader` existants selon le besoin réel ;
  - faire évoluer `NotificationInputFactory` pour consommer des fragments explicites plutôt que le budget global ;
  - supprimer l'import direct de `PersistenceManager`.
- **Tests** : conserver les tests d'inputs ; tests du dispatch automatique après commit et manuel ; règle ArchUnit dédiée.
- **Limite** : ne pas déplacer ici les canaux de notification ou les repositories de déduplication.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [x] Terminé

---

# SET-010 — Settings : définir l'ownership applicatif par domaine

- **Prérequis** : aucun.
- **Finalité** : faire de `/settings` une façade d'orchestration sans créer de « domaine Settings » global.
- **Ownership cible** :
  - Retraite : `birthYear`, `retireAge`, `pass2026`, `passGrowthRate`, paramètres AGIRC ;
  - Fiscalité : `childExitAge`, `taxAbattement`, tranches, overrides ;
  - Trésorerie : `pivotDate`, `pivotMode`, `startBalance`, sweep/cash ;
  - Objectifs : `goalSecureHorizonMonths`, `goalLiquidHorizonMonths` ;
  - Simulation : `simulateUntilAge` ;
  - Hypothèses économiques : `inflationRate`.
- **Travaux attendus** : formaliser le tableau d'ownership et identifier les champs encore traités comme « fiscaux/généraux ».
- **Livrable** : architecture clarifiée, sans changement nécessaire de l'URL REST.
- **Statut** : [ ] Non commencé / [ ] Démarré / [x] Terminé

---

# SET-020 — Settings : brancher chaque famille sur son owner

- **Prérequis** : SET-010, FIX-020.
- **Finalité** : remplacer la logique « tout ce qui n'est pas Objectifs va vers Fiscalité ».
- **Travaux attendus** : dispatch par propriété vers le service propriétaire ; conserver l'atomicité multi-domaines ; conserver la
  lecture composite ; introduire des ports/commandes adaptés lorsqu'un owner n'en possède pas encore.
- **Parallélisation** : les contrats Retraite, Fiscalité, Trésorerie, Objectifs, Simulation/Hypothèses peuvent être préparés séparément,
  mais l'intégration du dispatcher reste un patch unique.
- **Tests** : mise à jour d'un champ par owner ; combinaison de plusieurs owners ; rollback multi-domaines.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [x] Terminé
- **Livraison** : `SettingsCommandRouter` (package `command`) est la table de routage unique par propriété, utilisée par
  `PATCH /settings` (`ParametersServiceImpl`, qui garde `@Transactional` et le verrou du budget en premier) **et** par
  `ImpotsServiceImpl.saveImpotsConfig`. Owners : Retraite (`RetirementCommandService.updateRetirementSetting`),
  Fiscalité (`childExitAge`, `taxAbattement` seulement), Trésorerie (`TresorerieCommandService.updateTresorerieSetting`),
  Objectifs (inchangé), Simulation (`SimulationSettingsCommandService`), Hypothèses économiques
  (`EconomicAssumptionsCommandService`). Nouveaux ports : `RetirementSettingField`, `TresorerieSettingField`,
  `SimulationSettingsWriter`, `EconomicAssumptionsWriter` (+ méthodes sur `RetirementWriter` / `TresorerieWriter`).
  Le stockage reste `SettingsEntity` : les adapters réutilisent la mutation de transition `updateTaxSettings(String, Object)`
  (séparation relevant des `DB-xxx`). `TaxSettingField` et `TaxCommandService.updateTaxSettings` restent en place pour
  SET-030. Changement de comportement assumé : un champ Objectifs reçu par `saveImpotsConfig` est désormais écrit
  (il était ignoré sans erreur). `pass2026` / `passGrowthRate` : routés vers Retraite mais écrits dans la même copie
  qu'avant (SET-040).

---

# SET-030 — Settings : supprimer `updateTaxSettings(field,value)` comme fourre-tout

- **Prérequis** : SET-020.
- **Finalité** : ne plus exposer une mutation générique dont le nom suggère que les paramètres appartiennent à Fiscalité.
- **Travaux attendus** : commandes explicites par famille ; supprimer les usages applicatifs du couple `field/value` lorsque le champ
  possède un owner connu ; conserver uniquement une compatibilité explicitement documentée si réellement nécessaire.
- **Critère de sortie** : une nouvelle propriété Settings ne doit plus avoir besoin d'être ajoutée à un dispatcher générique Fiscalité.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [x] Terminé
- **Livraison** : `BudgetMutationService.updateTaxSettings(String, Object)` et `DomainMutations.updateTaxSettings` sont supprimés.
  Cinq mutations explicites, une par owner, les remplacent : `updateRetirementSetting(RetirementSettingField, Object)`,
  `updateTresorerieSetting(TresorerieSettingField, Object)`, `updateFiscalSetting(TaxSettingField, Object)`,
  `updateSimulateUntilAge(Object)`, `updateInflationRate(Object)` (événements `BudgetMutatedEvent` du même nom). Les adapters
  Retraite, Trésorerie, Fiscalité et Settings appellent chacun la mutation de leur famille. `TaxSettingField` est réduit à
  `CHILD_EXIT_AGE` et `TAX_ABATTEMENT` : Fiscalité n'expose plus que ses deux paramètres, `TaxWriter` / `TaxCommandService`
  gardent leur signature. `SettingsCommandRouter` n'a plus de filtre `TAX_OWNED`. Ajouter une propriété Settings = l'ajouter
  à l'enum de son owner et au `switch` de la mutation correspondante, sans toucher Fiscalité ; le test
  `UpdateTaxSettingsMinimalBudgetTest#everyOwnerSettingFieldIsApplied` détecte l'oubli. Le stockage reste `SettingsEntity`
  (séparation relevant des `DB-xxx`) ; `pass2026` / `passGrowthRate` restent écrits dans la même copie (SET-040). Aucun
  changement de contrat REST ni de comportement (valeurs par défaut de conversion inchangées).

---

# SET-040 — Settings : trancher et appliquer la duplication `pass2026/passGrowthRate`

- **Prérequis** : SET-010.
- **Finalité** : avoir une seule source métier pour les paramètres retraite.
- **Décision cible** : les deux paramètres appartiennent à Retraite. Aucun modèle métier Settings ne doit les posséder comme seconde copie.
- **Travaux attendus** : supprimer la duplication dans les modèles métier composites ; vérifier l'impact du contrat API `SettingsDto` ;
  si les champs restent exposés dans la façade REST pour compatibilité, les considérer comme une vue composite, pas comme une seconde
  propriété métier ; adapter les tests de mapping.
- **Point de vigilance** : ne pas casser `/budget` et les imports JSON sans migration de compatibilité.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [x] Terminé
- **Livraison** : `SettingsModel` ne porte plus `pass2026` / `passGrowthRate` (13 composants ; constructeurs de
  compatibilité à 9 et 12 arguments). La source unique est `RetirementModel`. `updateRetirementSetting` écrit
  `pass2026` / `passGrowthRate` dans la retraite (`BudgetMutationService.updateRetirementPass`) ; `birthYear` et
  `retireAge` restent dans `SettingsModel`. Les façades REST gardent les clés `pass2026` / `passGrowthRate` dans
  `settings` (contrat inchangé, `openapi.yaml` inchangé) comme **vue composite** relue depuis la retraite :
  `SettingsMapper.toResponseMap(model, objectifs, retirement)` (`ParametersServiceImpl` reçoit `RetirementReader`),
  `TaxMapper.toResponseMap(model, retirement)`, `StatementBankImportMapper.toSettingsMap(settings, retirement)`,
  `OverviewMapper` (via `BudgetFacadeView.retirement()`) et `RetraiteMapper`. Import JSON : si la retraite d'un
  `BudgetDataDto` ne porte pas ces valeurs, `OverviewMapper` reprend celles de `settings` (compatibilité des
  exports antérieurs) ; la retraite reste prioritaire. Stockage : les colonnes `pass2026` / `passGrowthRate` de
  `SettingsEntity` sont conservées mais plus lues ni écrites (écrites à `null`) ; leur suppression physique relève
  des `DB-xxx`. Tests : `UpdateTaxSettingsMinimalBudgetTest#passParametersAreWrittenToRetirementOnly`,
  `SettingsMapperTest#readsPassParametersFromRetirement`.

---

# ARCH-010 — ArchUnit : interdictions explicites par couche pure

- **Prérequis** : RES-010, NOTIF-010.
- **Finalité** : transformer les principes d'architecture en contraintes mécaniques.
- **Travaux attendus** : interdire dans les moteurs/calculs purs les dépendances vers `BudgetDataModel`, `PersistenceManager`, entités JPA,
  DTO OpenAPI et Spring lorsqu'il n'est pas nécessaire ; conserver le principe « nouvelle violation = échec » sans gel de dette.
- **Contrainte importante** : les assemblers/factories applicatifs peuvent continuer à dépendre des modèles de persistance pendant la transition ;
  la règle ne doit donc pas s'appliquer à `internal.factory` de manière aveugle.
- **Tests** : règle ArchUnit verte, avec vérification négative lors du patch.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [x] Terminé
- **Livraison** : `PureLayerRules` (règles), `PureLayerArchTest` (code de production) et `PureLayerRulesNegativeTest`
  (fixtures fautives dans `internal.calculation.archfixture`). Couches pures : `internal.calculation` hors
  `Jpa*` / `*SettingsService` / `*SettingsStore`, `internal.model`, `internal.notification.rules`. Interdits :
  `BudgetDataModel`, `internal.persistence..`, JPA / Spring Data, OpenAPI (`com.moe.myfamilybudget.api..`), Spring hors
  `org.springframework.stereotype`. `internal.factory` n'est pas visé. Correction nécessaire : la règle historique
  `PURE_DOMAIN_DOES_NOT_DEPEND_ON_OPENAPI_DTOS` visait `com.moe.myfamilybudget.server.api..` (package inexistant, règle
  sans effet) ; elle vise désormais `com.moe.myfamilybudget.api..`. Dette connue : les stores `Jpa*` et
  `*SettingsService` restent dans `internal.calculation` (déplacement hors périmètre).

---

# ARCH-020 — ArchUnit : interdictions de dépendances inter-domaines

- **Prérequis** : SET-030, ARCH-010.
- **Finalité** : préparer le graphe de modules Maven en codant les dépendances autorisées.
- **Travaux attendus** :
  - Retraite ne dépend pas des internals Fiscalité/Trésorerie ;
  - Fiscalité consomme la projection retraite, pas l'implémentation retraite ;
  - Trésorerie consomme les projections explicites ;
  - Patrimoine ne dépend pas des internals Trésorerie ;
  - Analyse/Overview sont consommateurs/agrégateurs ;
  - aucun domaine ne dépend d'un controller REST ou d'un DTO OpenAPI.
- **Critère** : les règles correspondent au graphe défini dans `00-principes.md`.
- **Limite** : ne pas imposer déjà les packages Maven finaux ; protéger les frontières logiques existantes.
- **Statut** : [ ] Non commencé / [x] Démarré / [ ] En attente / [ ] Terminé

---

# CLEAN-010 — `BudgetDataModel` : inventorier puis réduire les usages résiduels

- **Prérequis** : RES-010, SET-040.
- **Finalité** : vérifier qu'il ne reste que les usages légitimes du snapshot global.
- **Travaux attendus** : recenser `new BudgetDataModel(...)`, `getBudgetData()` / `setBudgetData()` et les factories recevant encore le modèle global ;
  classer chaque occurrence en `SNAPSHOT-GLOBAL`, `ASSEMBLY-TEMP` ou `LOCAL-LEAK` ; supprimer les `LOCAL-LEAK`.
- **Critère** : un nouveau use case métier ne peut plus réutiliser `BudgetDataModel` comme « DTO interne universel ».
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [x] Terminé
- **Livraison** : inventaire et classification dans `19-inventaire-budget-data-model.md`. Les cinq signatures `public` à base de
  `BudgetDataModel` (`TresorerieServiceImpl#computeTresorerie`, `#buildCategoryOptions`, `#computeRealAverages`,
  `#buildTresorerieSuggestions`, `PatrimoineServiceImpl#computePatrimoineProjections`) classées `LOCAL-LEAK` sont
  restreintes au package `impl`. `BudgetDataModelUsageArchTest` fixe la liste blanche des consommateurs autorisés
  (persistance, factories, mappers, updater, neuf services d'API) : un nouveau consommateur fait échouer la règle.
  Restent des `ASSEMBLY-TEMP` documentés (`composeBudgetData()` dans sept services, assemblage en ligne dans `RetraiteServiceImpl`) jusqu'aux `DB-xxx` ; l'isolement
  des opérations globales relève de `CLEAN-020`.

---

# CLEAN-020 — Snapshot global : isoler import/export/backup/reset

- **Prérequis** : CLEAN-010.
- **Finalité** : donner une frontière explicite aux opérations qui justifient encore `BudgetDataModel`.
- **Travaux attendus** : centraliser le snapshot global dans un composant applicatif/transverse ; conserver `/budget`, `/budget/import`,
  `/budget/reset` comme opérations transverses ; ne pas propager le snapshot dans les services métiers locaux ; documenter cette exception.
- **Tests** : import/export/reset ; fixture de référence ; restart PostgreSQL ; E2E F1.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Terminé

---

# DOC-010 — Réconcilier les statuts des backlogs et de la checklist

- **Prérequis** : FIX-010, FIX-020, SET-040, ARCH-020.
- **Finalité** : éviter qu'un agent futur interprète des constats hors périmètre comme des VT non terminés.
- **Travaux attendus** :
  - `17-backlog-tests-patchs.md` : conserver VT-330 et VT-340 à `Terminé`, mais retirer les formulations indiquant qu'ils sont encore ouverts
    une fois les FIX correspondants terminés ;
  - `14-checklist-maven.md` : distinguer « patch VT terminé » de « défaut résiduel corrigé » ;
  - `18-backlog-persistance-patchs.md` : ajouter les nouveaux prédécesseurs `FIX-*`, `SET-*`, `ARCH-*`.
- **Livrable** : documentation cohérente avec le code réellement présent.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] Terminé

---

# GATE-010 — Gate final avant reprise DB / ouverture Maven

- **Prérequis** : tous les patchs précédents.
- **Finalité** : vérifier qu'il ne reste plus de dette connue bloquante avant le passage aux modules.
- **Contrôles obligatoires** :

### Tests

- `mvn -f back/server/pom.xml -B test`
- variante PostgreSQL effectivement exécutée ;
- `bun run test` ;
- `bun run test:e2e` ;
- `node tests/gate/check-gate.js all`.

### Architecture

- ArchUnit vert ;
- aucune dépendance calcul → `BudgetDataModel` ;
- aucune dépendance calcul → `PersistenceManager` ;
- aucune dépendance calcul → JPA ;
- aucune dépendance domaine → OpenAPI DTO ;
- aucune dépendance inter-domaine interdite.

### Persistance

- commands propriétaires utilisées sur les mutations ordinaires ;
- `PersistenceManager` réduit à une façade clairement transverse/de transition ;
- cache mémoire cohérent avec DB ;
- BankImport propage les échecs.

### Modèles

- aucun `ResultModel` métier ne transporte `BudgetDataModel` ;
- `BudgetDataModel` réservé au snapshot global et aux assemblages explicitement documentés ;
- Settings ownership explicite ;
- duplication des paramètres Retraite tranchée.

### API

- split OpenAPI déjà livré et bundling/génération fonctionnels ;
- contrats composites documentés ;
- aucune frontière de module ne dépend nécessairement du monolithe OpenAPI.

## Critère de sortie

Lorsque `GATE-010` est vert **et que les migrations `DB-xxx` prévues comme préalables dans
`18-backlog-persistance-patchs.md` sont elles-mêmes terminées**, il ne doit plus rester de frein architectural
identifié pour créer les modules Maven.

Il restera alors à ouvrir **un nouveau backlog dédié au découpage Maven lui-même** :

- structure du reactor Maven ;
- modules et dépendances autorisées ;
- déplacement des packages ;
- contrats inter-modules ;
- dépendances techniques communes ;
- tests inter-modules ;
- ordre des extractions.

Ce nouveau backlog ne doit pas contenir de « dette de préparation » supplémentaire : les modules doivent matérialiser
des frontières déjà testées, pas servir à découvrir pour la première fois les dépendances.

---

## Graphe recommandé

```text
                           main
                            │
          ┌─────────────────┼─────────────────┐
          ↓                 ↓                 ↓
      FIX-010           FIX-020           RES-010
     BankImport       sweepEnabled       Results/Mappers
          │                 │                 │
          └────────────┬────┴─────────────────┘
                       │
             ┌─────────┴─────────┐
             ↓                   ↓
        NOTIF-010             SET-010
             │                   │
             │              ┌────┴─────┐
             │              ↓          ↓
             │           SET-020    SET-040
             │              ↓
             │           SET-030
             │              │
             └────────┬─────┘
                      ↓
                 ARCH-010
                      ↓
                 ARCH-020
                      ↓
                 CLEAN-010
                      ↓
                 CLEAN-020
                      ↓
                  DOC-010
                      ↓
                  GATE-010
                      │
                      ↓
             reprise/fin DB-xxx
                      │
                      ↓
              **Maven modules**
```

## Parallélisation

Le premier lot recommandé est :

```text
FIX-010   FIX-020   RES-010   NOTIF-010   SET-010
   \\         |         |          |          /
                  puis intégration
```

`ARCH-010` peut commencer dès que les frontières concernées sont stables, et `ARCH-020` seulement après les
décisions Settings et commands.

Les seules zones à sérialiser sont :

- le dispatcher Settings (`SET-020` → `SET-030`) ;
- la réduction globale `BudgetDataModel` (`CLEAN-010` → `CLEAN-020`) ;
- le gate final.

## Résultat attendu

Le passage de ce fichier n'a pas pour objectif de « rendre le code parfait ». Il doit rendre les frontières
**explicites, testées, et suffisamment stables pour que Maven puisse les imposer physiquement**.

La séparation fine des entités JPA peut continuer domaine par domaine après le passage Maven si elle n'empêche pas
les contrats et dépendances inter-modules ; en revanche, aucune dette connue du présent fichier ne doit rester
silencieusement cachée au moment du gate final.
