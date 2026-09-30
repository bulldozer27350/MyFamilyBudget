# 17 — Backlog de patchs pour la sécurisation des tests avant Maven

Statut : 🟡 à valider

## Lecture obligatoire avant tout patch

Tout agent qui prend un patch de cette liste lit d'abord :

1. [`00-principes.md`](00-principes.md) — règles d'architecture et dépendances interdites ;
2. [`01-sequencement.md`](01-sequencement.md) — séquencement général ;
3. [`16-tests.md`](16-tests.md) — stratégie et critères de sortie ;
4. le fichier de domaine concerné si le patch touche un comportement métier.

## Règles de livraison

Les agents travaillent de préférence sur `feature/VT-xxx`. Un patch ne dépend que des identifiants listés dans
la colonne « Prérequis » ; les branches parallèles doivent éviter de modifier simultanément le même helper ou la
même fixture. Les helpers communs sont donc stabilisés dans `VT-000` avant que les scénarios E2E se multiplient.

- Un patch = un item de cette liste.
- Chaque patch doit laisser le build et les tests existants dans un état vert.
- Pas de refactoring métier opportuniste dans un patch de tests.
- Les tests doivent viser les contrats comportementaux ; éviter les assertions sur les implémentations
  internes lorsqu'elles ne sont pas nécessaires.
- Un patch peut uniquement ajouter des helpers si ceux-ci réduisent réellement les conflits des patchs suivants.
- Toute dépendance PostgreSQL spécifique doit être explicitement signalée dans le patch.

## Vue d'ensemble

| ID | Titre | Prérequis | Parallélisable avec |
|---|---|---|---|
| VT-000 | Socle de test commun et dataset de référence | aucun | VT-100, VT-200, VT-300, VT-400 |
| VT-100 | Caractérisation backend des endpoints critiques | VT-000 | VT-200, VT-300, VT-400 |
| VT-200 | Mode Playwright sans fallback + contexte vierge | VT-000 | VT-100, VT-300, VT-400 |
| VT-300 | Tests de mapping ciblés | VT-000 | VT-100, VT-200, VT-400 |
| VT-400 | Renforcement ArchUnit et règles anti-régression | VT-000 | VT-100, VT-200, VT-300 |
| VT-110 | Scénario backend transversal Retraite→Fiscalité→Trésorerie→Overview | VT-100 | VT-120, VT-210, VT-500 |
| VT-120 | Scénario backend Banque→Pointage→Analyse | VT-100 | VT-110, VT-210, VT-500 |
| VT-210 | Scénarios frontend lecture + reload | VT-200, VT-100 | VT-110, VT-120 |
| VT-220 | Scénario frontend mutation patrimoine→trésorerie→overview | VT-210 | VT-110, VT-120 |
| VT-230 | Scénario frontend paramètres multi-domaines | VT-210 | VT-220 |
| VT-240 | Scénario diagnostique backend indisponible | VT-200 | VT-210, VT-220, VT-230 |
| VT-310 | Renforcer `PersistenceAdaptersTest` | VT-100 | VT-110, VT-120 |
| VT-320 | Test de persistance après redémarrage Spring | VT-110, VT-310 | VT-220 |
| VT-330 | Test d'échec d'écriture : mémoire non modifiée | VT-310 | VT-320 |
| VT-340 | Test d'atomicité des mutations multi-domaines | VT-310 | VT-320 |
| VT-350 | Test de concurrence sur mutations critiques | VT-310 | VT-320 |
| VT-500 | Suite E2E de référence finale | VT-210, VT-220, VT-230, VT-240 | VT-320, VT-330, VT-340, VT-350 |
| VT-600 | Gate de validation avant Maven | VT-320, VT-330, VT-340, VT-350, VT-400, VT-500 | aucun |

---

## VT-000 — Socle de test commun et dataset de référence

- **Prérequis** : aucun
- **Fichiers à lire** : `16-tests.md`, `tests/e2e/functional.spec.js`, fixtures existantes.
- **Objectif** : fournir une base déterministe commune sans toucher au code métier.
- **Travaux** : documenter le dataset canonique, centraliser les constantes d'API/frontend Playwright si cela
  réduit les duplications, définir le nettoyage d'état navigateur et le mécanisme de reset/import.
- **Limite** : ne pas modifier le contrat métier.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [x] Terminé
- **Livré** : `tests/e2e/helpers/` (constantes, `expectBackendCall`/`waitForReactMount`, reset/import,
  `clearBrowserState`), `tests/e2e/socle.spec.js`, `tests/e2e/README.md` (dataset canonique et mécanisme de
  reset/import). `functional.spec.js` ne fait plus qu'importer ces helpers.

## VT-100 — Caractérisation backend des endpoints critiques

- **Prérequis** : VT-000
- **Objectif** : transformer les réponses actuelles importantes en filet de sécurité explicite.
- **Travaux** : ajouter des assertions ciblées pour Retraite, Fiscalité, Trésorerie, Patrimoine, Prêts,
  Overview et Analyse à partir du dataset de référence.
- **Livrable** : tests backend stables, exécutables sans navigateur.
- **Ne pas faire** : refactorer les calculateurs.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [x] Terminé
- **Livré** : `CriticalEndpointsCharacterizationTest` (`@SpringBootTest` + `MockMvc`, sans navigateur, dataset
  `mock-budget.json` identique à `tests/e2e/fixtures/budget-familial.json`), complémentaire de
  `BusinessLogicIntegrationTest` : Retraite (contrat JSON complet de la projection, dont la clé historique
  `tauxAppliqué`), Fiscalité (barème par défaut, parts, impôt et taux PAS 2026-2028, cohérence avec la
  trésorerie), Trésorerie (horizon 2026-2075, cashflow 2027/2028, invariant du cumul), Patrimoine (PEA
  2027/2028/2054 et totaux), Overview (cohérence croisée avec Retraite, Trésorerie et Patrimoine), Analyse
  (état sans import bancaire). Aucune assertion ne dépend de la date du jour (`taxPreview` est contrôlé
  relativement à l'année courante). Prêts : déjà caractérisés par `testAnalysePrets_*` (dataset sans prêt),
  non dupliqués. Valeurs attendues capturées à partir des moteurs sur HEAD `5954468`. Exécuté en CI (correctif VT-100b : dernière
  tranche du barème sans clé `upTo` ; après la retraite, le cashflow de `/overview` inclut les pensions
  alors que celui de `/tresorerie` reste à 0, comportement caractérisé tel quel).

## VT-200 — Mode Playwright sans fallback + contexte vierge

- **Prérequis** : VT-000
- **Objectif** : rendre impossible un faux vert dû au fallback JS.
- **Travaux** : ajouter le paramètre/config de test pour `DISABLE_JS_FALLBACK=true`, créer un contexte vierge ou
  nettoyer `localStorage` avant les scénarios critiques, exposer un helper Playwright commun qui attend une
  réponse backend pour les lectures/écritures critiques.
- **Contrainte** : ne pas changer la valeur par défaut production (`false`).
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [x] Terminé
- **Livré** : override de test dans `view/config.js` (`sessionStorage["mfb.test.disableJsFallback"] = "true"`
  force `DISABLE_JS_FALLBACK=true` ; valeur par défaut inchangée à `false`), `tests/e2e/helpers/browser.js`
  (`disableJsFallback`, `expectFreshBrowserState`, `gotoAndExpectBackend`, `actAndExpectBackend`,
  `readJsFallbackFlag`), constante `NO_FALLBACK_STORAGE_KEY`, `tests/e2e/no-fallback.spec.js`,
  section dédiée dans `tests/e2e/README.md`.

## VT-300 — Tests de mapping ciblés

- **Prérequis** : VT-000
- **Objectif** : séparer les erreurs de mapping des erreurs de logique métier.
- **Travaux** : tests unitaires ciblés pour les conversions DTO ↔ modèles domaine et modèles ↔ API sur les
  contrats déjà en mouvement.
- **Critère** : tests rapides, sans Spring lorsque possible.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [x] Terminé
- **Livré** : `SettingsMapperTest` (contrat `GET /settings`, réinjection des seuils Objectifs RF-700, catégories
  d'actifs, valeurs par défaut) et `NotificationsMapperTest` (modèle ↔ DTO, valeurs par défaut, corps absent),
  sans Spring. Les mappers `Analyse Prêts`, `Suggestions taux`, `Taux marché` et `EnableBanking` étaient déjà
  couverts ; restent sans test dédié : `Overview`, `Analyse`, `Tresorerie`, `Patrimoine`, `Retraite`, `Tax`,
  `Pointage`, `StatementBankImport` (à traiter au fil des migrations qui les déplacent).

## VT-400 — Renforcement ArchUnit et règles anti-régression

- **Prérequis** : VT-000
- **Objectif** : empêcher toute réintroduction de dépendance globale pendant les migrations.
- **Travaux** : compléter les règles existantes pour les calculateurs migrés et vérifier notamment l'absence de
  dépendance vers `BudgetDataModel`, `PersistenceManager` et DTO OpenAPI dans les couches qui doivent être pures.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [x] Terminé
- **Livré** : Règle ArchUnit `ENGINES_DO_NOT_DEPEND_ON_PERSISTENCE_MANAGER` (s'assure que `internal.calculation`, `internal.model` et `internal.notification.rules` ne dépendent pas de `PersistenceManager`) et règle `PURE_DOMAIN_DOES_NOT_DEPEND_ON_OPENAPI_DTOS` (s'assure que les couches pures du domaine ne dépendent pas des DTO OpenAPI dans `com.moe.myfamilybudget.server.api..`) dans `CalculationDependenciesArchTest.java`.

## VT-110 — Scénario backend Retraite→Fiscalité→Trésorerie→Overview

- **Prérequis** : VT-100
- **Objectif** : valider le graphe de calcul complet et détecter une double implémentation de projection retraite.
- **Travaux** : import fixture, appels `retraite`, `impots`, `tresorerie`, `overview`, assertions croisées sur
  année de retraite, pension, impôts, cash-flow et KPI.
- **Parallèle** : peut être développé en parallèle de VT-120 et VT-210.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [x] Terminé
- **Livré** : `RetirementToOverviewScenarioTest` (`@SpringBootTest` + `MockMvc`, dataset `mock-budget.json`),
  complémentaire de `CriticalEndpointsCharacterizationTest` : chaque assertion relie au moins deux endpoints.
  Année de retraite identique dans `/retraite`, `/tresorerie` et `/overview` ; une seule projection retraite
  (pension de `/retraite` = `totalPensions` et revenu du cashflow `/overview`, constant jusqu'à l'horizon) ;
  impôts cohérents entre `/impots`, `/tresorerie` et `/overview` avant la retraite, pension imposée ensuite ;
  identité `net = revenus + variables − épargne − charges − exceptionnels − impôts` sur les deux cashflows ;
  écart `/overview` / `/tresorerie` limité aux pensions après la retraite (comportement actuel de
  `/tresorerie` : aucun revenu, solde figé, caractérisé tel quel) ; KPI (`fluxNetActuel`, `retirePatrimoine` =
  placements + immobilier réévalué, règle des 4 % = patrimoine / 300). Valeurs capturées sur HEAD `7f56a53`.

## VT-120 — Scénario backend Banque→Pointage→Analyse

- **Prérequis** : VT-100
- **Objectif** : figer la cohérence entre import bancaire, pointage et analyse.
- **Travaux** : dataset avec transactions, catégories, matchings et opérations engagées ; assertions sur
  montants et catégories.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [x] Terminé
- **Livré** : `BankPointageAnalyseScenarioTest` (`@SpringBootTest` + `MockMvc`) : dataset construit relativement
  au mois courant (M0) et au précédent (M1), avec catégories (dont compressibles), transactions (dont une
  ventilée en deux catégories, référencée par `tx_9#s1` dans le pointage), transaction non catégorisée,
  pointages sur deux mois et opération en cours. Lignes budgétaires à montants constants (début ancien,
  croissance et inflation nulles) pour rester indépendant de la date. Couvre : `GET /pointage` (données
  importées restituées), KPI et `categorySummaries` d'Analyse (ventilations réparties, tri, couleurs),
  `landingData` du mois courant (réel pointé + opération en cours, statuts), `monthlyCompareData`,
  `driftRows` (moyennes 3/12 mois, écart, statut, absence de moyenne sans pointage), propagation d'un
  `PUT /pointage/matchings/{mois}` vers `/pointage` et `/analyse`, fenêtre `monthsBack`.
  Valeurs attendues calculées à la main d'après `AnalyseCalculator` (HEAD `3d77990`) ; à confirmer en CI.
  Correctif VT-120b : les catégories du dataset utilisent `kind` = `Dépense` (valeur de l'enum OpenAPI
  `BankImportCategoryDto`) ; `Depense` sans accent provoquait un HTTP 500 sur `POST /budget/import`.

## VT-210 — Scénarios frontend lecture + reload

- **Prérequis** : VT-200, VT-100
- **Objectif** : vérifier que les écrans consomment réellement le backend.
- **Travaux** : durcir les tests existants Overview, Trésorerie, Patrimoine, Settings et Analyse avec
  `expectBackendCall`, contexte vierge et assertions métier minimales.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

## VT-220 — Scénario frontend mutation patrimoine→trésorerie→overview

- **Prérequis** : VT-210
- **Objectif** : valider une mutation dont l'effet traverse plusieurs domaines.
- **Travaux** : créer/modifier un placement, relire Patrimoine, Trésorerie et Overview, puis reload.
- **Critère** : aucune assertion ne doit dépendre d'un état JS non relu du serveur.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

## VT-230 — Scénario frontend paramètres multi-domaines

- **Prérequis** : VT-210
- **Objectif** : tester la façade Settings après séparation de l'ownership.
- **Travaux** : modifier au moins deux familles de paramètres de propriétaires distincts, relire Settings,
  puis les vues impactées après reload.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

## VT-240 — Scénario diagnostique backend indisponible

- **Prérequis** : VT-200
- **Objectif** : prouver qu'en mode diagnostic le fallback ne masque plus une panne serveur.
- **Travaux** : couper le backend, charger une vue critique, déclencher une lecture et une écriture, vérifier
  l'échec explicite.
- **Limite** : test négatif ; ne doit pas rendre le fallback indisponible par défaut.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

## VT-310 — Renforcer `PersistenceAdaptersTest`

- **Prérequis** : VT-100
- **Objectif** : passer de simples vérifications non-null à des assertions de round-trip.
- **Travaux** : write → read, objets critiques et cas reset/import.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [x] Terminé
- **Livré** : `PersistenceAdaptersTest` réécrit en trois blocs, sans plus aucun simple `isNotNull()` :
  état par défaut après `init()` (valeurs par défaut des paramètres, de la retraite et du barème en 5 tranches) ;
  round-trip après import complet (`setBudgetData` avec chaque domaine renseigné, relu par les huit adaptateurs :
  revenus, charges, dépenses ponctuelles, variables, placements avec historique, immobilier, catégories d'actifs,
  virements, retraite avec personne, impôts, import bancaire, prêts, objectifs avec allocations, paramètres ;
  un second import remplace le premier) ; round-trip après mutation ciblée (`updateRetirement`,
  `updateTaxConfig` y compris listes nulles, `resetDefaultTaxBrackets`, `addAssetCategory` /
  `removeAssetCategory`, `updateBankImport` y compris `null`, lecture « vivante » d'un même adaptateur) ; cas
  reset / import nul (`resetData`, `setBudgetData(null)`, barème vide relu comme barème par défaut, import après
  reset). Repositories mockés (`PersistenceManagerTestFactory`) : la persistance JPA réelle relève de VT-320.
  Constructeurs et accesseurs des modèles vérifiés par compilation sur HEAD `64484f6` ; exécution à confirmer en CI.

## VT-320 — Test de persistance après redémarrage Spring

- **Prérequis** : VT-110, VT-310
- **Objectif** : démontrer que la donnée n'existe pas seulement en mémoire.
- **Travaux** : mutation, GET, redémarrage du contexte Spring, GET de contrôle.
- **Contrainte** : exécuter au moins une variante PostgreSQL pour les migrations JPA significatives.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

## VT-330 — Test d'échec d'écriture : mémoire non modifiée

- **Prérequis** : VT-310
- **Objectif** : protéger l'ordre actuel voulu `DB → mémoire` dans `BudgetCacheStore.applyAndPersist`.
- **Travaux** : provoquer une erreur de persistance, vérifier que l'ancien état mémoire reste visible et que
  la nouvelle valeur n'est pas servie artificiellement.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [x] Terminé
- **Livré** : `WriteFailureKeepsMemoryTest` (repositories mockés, sans Spring ni base) : deux points de
  défaillance simulés — écriture de l'entité principale (`budgetDataRepository.save`) et écriture des lignes
  enfants (`incomeRepository.save`, base déjà partiellement modifiée). Pour chaque mutation de `PersistenceManager`
  (`addTresorerieRow`, `updateTresorerieRow`, `removeTresorerieRow`, `savePatrimoineRow`, `deletePatrimoineRow`,
  `updateRetirement`, `updateTaxConfig`, `updateTaxSettings`, `addAssetCategory`, `updateBankImport`,
  `setBudgetData`, et `resetData` / `setBudgetData(null)` pour l'entité principale) : l'exception d'origine remonte
  telle quelle, `getBudgetData()` renvoie toujours la même instance, les huit ports de lecture restituent l'ancien
  état et aucun `BudgetMutatedEvent` n'est publié. Test de reprise : une fois la base revenue, la même mutation
  aboutit et n'est visible qu'à ce moment-là. Constat hors périmètre, non modifié :
  `BudgetPersistenceGateway.saveBankImport` intercepte les exceptions (journalisées) ; une panne limitée à
  l'écriture du blob d'import bancaire ne remonte donc pas et la mémoire est tout de même mise à jour.
  Exécution à confirmer en CI (compilation non vérifiée localement).

## VT-340 — Test d'atomicité des mutations multi-domaines

- **Prérequis** : VT-310
- **Objectif** : éviter une Settings ou importation partiellement appliquée.
- **Travaux** : mutation valide multi-propriétaires ; mutation invalide au milieu ; vérifier rollback complet.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [x] Terminé
- **Décision** : option (a) retenue — les façades multi-domaines deviennent transactionnelles (patch de code inclus, le test
  ne pouvant pas passer sur l'existant).
- **Livré** : `@Transactional` sur `SystemeServiceImpl.importJSON` / `resetData` et `ParametersServiceImpl.saveSettings`
  (écritures budget puis Objectifs dans une seule transaction). `BudgetCacheStore` mémorise, à la première mutation d'une
  transaction, l'état mémoire d'avant et le rétablit si la transaction ne se termine pas par un commit (le cache était
  sinon en avance sur une base annulée) ; sans transaction active, comportement inchangé. Tests :
  `BudgetCacheStoreRollbackTest` (unitaire : rollback de plusieurs mutations, commit, `setBudgetData` / `resetData`, sauvegarde
  en échec) et `MultiDomainAtomicityTest` (`@SpringBootTest`, base H2 dédiée, store Objectifs espionné en échec) : import,
  réinitialisation et `PUT /settings` (champ Fiscalité appliqué puis champ Objectifs en échec) laissent inchangés le cache, la base
  relue directement et les paramètres Objectifs ; la même mise à jour aboutit en entier sans échec. Point de vigilance pour
  VT-350 : une transaction multi-étapes garde désormais ses verrous base entre deux écritures alors que `mutationLock` est pris
  écriture par écriture ; à observer dans le test de concurrence. Constat hors périmètre, non corrigé :
  `BudgetMutationService.updateTaxSettings` lève une `NullPointerException` (déballage de `sweepEnabled` nul) dès qu'on modifie
  un champ quelconque des paramètres alors que `sweepEnabled` est absent des données importées (cas de `mock-budget.json`) ;
  `MultiDomainAtomicityTest` renseigne donc `sweepEnabled: false` dans son propre dataset, sans toucher au fichier partagé. Exécution à confirmer en CI (compilation non vérifiée localement).

## VT-350 — Test de concurrence sur mutations critiques

- **Prérequis** : VT-310
- **Objectif** : documenter le comportement quand deux écritures concurrentes visent la même ressource.
- **Travaux** : deux appels simultanés, vérifier invariants, absence de corruption et résultat final déterministe
  ou explicitement documenté.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

## VT-500 — Suite E2E de référence finale

- **Prérequis** : VT-210, VT-220, VT-230, VT-240
- **Objectif** : disposer d'un petit nombre de parcours représentatifs avant Maven.
- **Travaux** : F1 à F6 de `16-tests.md`, nettoyage des scénarios redondants et stabilisation des fixtures.
- **Critère** : exécution répétable, sans dépendance à une donnée locale.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

## VT-600 — Gate de validation avant Maven

- **Prérequis** : VT-320, VT-330, VT-340, VT-350, VT-400, VT-500
- **Objectif** : transformer les tests en critère de passage vers la séparation Maven.
- **Travaux** : documenter la commande exacte de CI/local pour backend, Playwright et PostgreSQL ; vérifier
  qu'aucun test obligatoire n'est uniquement "toléré" ou désactivé.
- **Livrable** : section de [14-checklist-maven.md](14-checklist-maven.md) complétée avec les identifiants VT.
- **Statut** : [ ] Non commencé / [ ] Démarré / [ ] En attente / [ ] Annulé / [ ] Terminé

## Graphe de parallélisation recommandé

```text
VT-000
 ├── VT-100 ──┬── VT-110 ───────────────┐
 │            └── VT-120 ───────────────┤
 ├── VT-200 ──┬── VT-210 ──┬── VT-220 ─┤
 │            │             └── VT-230 ─┤
 │            └── VT-240 ───────────────┤
 ├── VT-300 ────────────────┐           │
 └── VT-400 ────────────────┼───────────┤
                             ↓           ↓
                           VT-310     VT-500
                             ↓
                   VT-320 / 330 / 340 / 350
                             └──────→ VT-600
```

Le point important pour la parallélisation est que **VT-100, VT-200, VT-300 et VT-400 ne se bloquent pas
mutuellement**. Les patchs de scénarios ne démarrent qu'après le socle dont ils ont besoin ; les tests de
redémarrage sont volontairement isolés de la suite E2E pour éviter qu'un chantier JPA long bloque tout le monde.
