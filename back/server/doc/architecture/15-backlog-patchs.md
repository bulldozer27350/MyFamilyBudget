# 15 — Backlog de patchs pour le chantier de découplage de `BudgetDataModel`

Statut : liste de travail, à exécuter patch par patch dans l'ordre des prérequis.

## Lecture obligatoire avant tout patch de cette liste

Tout agent (humain ou IA) qui prend un patch de cette liste doit d'abord lire, dans l'ordre :

1. [`00-principes.md`](00-principes.md) — règles interdites, test de conception à 4 questions,
   graphe de dépendances cible, anti-cycles. C'est la constitution du chantier.
2. [`01-sequencement.md`](01-sequencement.md) — l'ordre de migration retenu et sa justification.

Chaque patch ci-dessous liste, en plus de ces deux fichiers, le ou les fichiers de domaine
spécifiques à relire avant de le démarrer.

## Règles pour chaque patch

- **Taille** : un patch = un item de cette liste. Ne pas regrouper plusieurs items dans un seul
  patch, même si cela semble plus rapide — l'objectif est justement de pouvoir merger et reviewer
  chaque étape indépendamment.
- **État du dépôt après merge** : chaque patch, une fois mergé, doit laisser le build et les
  tests verts. Un patch qui casse la compilation ou les tests de caractérisation de `RF-000`
  n'est pas mergeable en l'état.
- **Pas de renommage/déplacement de fichiers hors du périmètre du patch.** Si un renommage semble
  nécessaire pour un patch donné, le signaler dans le statut plutôt que de l'inclure
  silencieusement.
- **Format des patchs** : LF uniquement, encodage UTF-8, un `git format-patch` par item de cette
  liste, validé par `git apply --check` sur un clone propre avant d'être proposé en revue.
- **Test de conception (`00-principes.md`)** : si, en écrivant un patch, la réponse à
  « quelle projection minimale le consommateur doit-il recevoir ? » redevient
  « il lui faut `BudgetDataModel` », s'arrêter et signaler le blocage plutôt que de continuer.
- **Point ouvert Patrimoine/Trésorerie** : le patch `RF-400` ne doit pas démarrer sans qu'une
  décision explicite ait été actée sur ce point (voir le patch lui-même).

## Vue d'ensemble

| ID | Titre | Domaine | Prérequis |
|---|---|---|---|
| RF-000 | Sécurisation avant chantier | Transverse | aucun |
| RF-001 | Garde-fou ArchUnit minimal | Transverse | RF-000 |
| RF-100 | Retraite - Contrats | Retraite | RF-001 |
| RF-101 | Retraite - Moteur centralisé | Retraite | RF-100 |
| RF-102 | Retraite - Brancher Trésorerie et Overview sur le moteur unique | Retraite | RF-101 |
| RF-103 | Retraite - Tests de composant + garde-fou | Retraite | RF-102 |
| RF-200 | Fiscalité - Contrats (version large) | Fiscalité | RF-001 |
| RF-201 | Fiscalité - Branchement du moteur | Fiscalité | RF-200 |
| RF-202 | Fiscalité - Réduction (sortie de findEarliestYear et de la projection retraite) | Fiscalité | RF-201, RF-102 |
| RF-203 | Fiscalité - Tests de composant + garde-fou | Fiscalité | RF-202 |
| RF-300 | Patrimoine - Contrats | Patrimoine | RF-001 |
| RF-301 | Patrimoine - Séparation des deux moteurs et branchement | Patrimoine | RF-300 |
| RF-302 | Patrimoine - Tests de composant + garde-fou | Patrimoine | RF-301 |
| RF-400 | Trésorerie - Contrats (et décision du point ouvert Patrimoine/Trésorerie) | Trésorerie | RF-202, RF-102, RF-301 |
| RF-401 | Trésorerie - Branchement et extraction des fonctions unitaires | Trésorerie | RF-400 |
| RF-402 | Trésorerie - Tests de composant + garde-fou | Trésorerie | RF-401 |
| RF-500 | Pointage - Contrats | Banque / Pointage | RF-001 |
| RF-501 | Pointage - Branchement | Banque / Pointage | RF-500 |
| RF-502 | Pointage - Tests de composant + garde-fou | Banque / Pointage | RF-501 |
| RF-600 | Analyse - Contrats | Analyse | RF-500 |
| RF-601 | Analyse - Branchement et suppression de la fuite de résultat | Analyse | RF-600 |
| RF-602 | Analyse - Tests de composant + garde-fou | Analyse | RF-601 |
| RF-700 | Objectifs - Contrat et ownership des settings | Objectifs | RF-001 |
| RF-701 | Notifications - Contrats (trois entrées distinctes) | Notifications | RF-700 |
| RF-702 | Notifications - Branchement et suppression de NotificationContext | Notifications | RF-701 |
| RF-703 | Notifications - Tests de composant + garde-fou | Notifications | RF-702 |
| RF-800 | Prêts - Contrats | Crédit | RF-001 |
| RF-801 | Prêts - Branchement | Crédit | RF-800 |
| RF-802 | Suggestions de taux - Contrat et branchement | Crédit | RF-001 |
| RF-803 | Prêts / Suggestions - Tests de composant + garde-fou | Crédit | RF-801, RF-802 |
| RF-900 | Overview - Contrat OverviewInput | Overview | RF-102, RF-202, RF-301, RF-401 |
| RF-901 | Overview - Branchement et suppression de la fuite de résultat | Overview | RF-900 |
| RF-902 | Overview - Tests de composant + garde-fou | Overview | RF-901 |
| RF-A00 | Mutations - CommandServices par domaine | Transverse | RF-103, RF-203, RF-302, RF-402, RF-502, RF-602, RF-703, RF-803, RF-902 |
| RF-B00 | Persistance palier 1 - Ports de lecture par domaine | Persistance | RF-A00 |
| RF-B01 | Persistance palier 1 - Branchement des moteurs sur les ports | Persistance | RF-B00 |
| RF-C00 | OpenAPI - Split par domaine | Transverse | RF-B01 |
| RF-D00 | Point d'arrêt - Revue de la checklist Maven | Transverse | RF-C00 |

---

### RF-000 — Sécurisation avant chantier

- **Domaine** : Transverse
- **Prérequis** : aucun
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : aucun (les deux fichiers du bandeau suffisent)
- **Modifications attendues** :
- Vérifier que le build et les tests backend/e2e existants sont verts.
- Ajouter des tests de caractérisation (snapshots des réponses API actuelles) pour les endpoints fiscal, retraite, trésorerie, patrimoine et prêts, utilisés comme filet de sécurité pendant tout le chantier.
- Aucune modification du code de production.
- **Statut** :
- [ ] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [x] Terminé
- [ ] Constaté comme mergé

### RF-001 — Garde-fou ArchUnit minimal

- **Domaine** : Transverse
- **Prérequis** : RF-000
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : aucun (les deux fichiers du bandeau suffisent)
- **Modifications attendues** :
- Ajouter la dépendance ArchUnit au module `back/server`.
- Écrire une règle initiale via `FreezingArchRule` (mécanisme de gel des violations existantes) : le package `calculation` ne doit pas dépendre de `BudgetDataModel`. Le gel capture les violations actuelles sans les corriger, et fait échouer uniquement toute nouvelle violation.
- Documenter dans le test lui-même que la liste des violations gelées doit se réduire domaine par domaine au fil des patchs RF-1xx à RF-9xx (chaque patch de garde-fou de domaine retire ce domaine de la liste gelée).
- **Statut** :
- [ ] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [x] Terminé
- [ ] Constaté comme mergé

### RF-100 — Retraite - Contrats

- **Domaine** : Retraite
- **Prérequis** : RF-001
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `03-domaine-retraite.md`
- **Modifications attendues** :
- Créer les records `RetirementCalculationInput`, `RetirementParameters`, `RetirementPersonInput`, `SalaryHistoryEntry`, `AnnualSalaryProjection`.
- Créer `RetirementInputFactory` qui construit l'Input à partir de `BudgetDataModel`, y compris la résolution `incomeLabel -> AnnualSalaryProjection` (actuellement une recherche dans `IncomeModel`).
- Purement additif : aucun appelant existant n'est modifié.
- **Statut** :
- [ ] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [x] Terminé
- [ ] Constaté comme mergé

### RF-101 — Retraite - Moteur centralisé

- **Domaine** : Retraite
- **Prérequis** : RF-100
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `03-domaine-retraite.md`
- **Modifications attendues** :
- Créer/consolider `RetirementCalculationService.compute(RetirementCalculationInput): RetirementProjection` à partir de la logique actuelle de `RetraiteServiceImpl` (qui devient la version canonique).
- Brancher `RetraiteServiceImpl` sur `RetirementInputFactory` + `RetirementCalculationService` au lieu de son calcul interne.
- Ne pas encore toucher `OverviewCalculationService` ni `TresorerieCalculationService` (voir RF-102).
- **Statut** :
- [ ] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [x] Terminé
- [ ] Constaté comme mergé

### RF-102 — Retraite - Brancher Trésorerie et Overview sur le moteur unique

- **Domaine** : Retraite
- **Prérequis** : RF-101
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `03-domaine-retraite.md`, `06-domaine-tresorerie.md`, `11-domaine-overview.md`
- **Modifications attendues** :
- Remplacer `TresorerieCalculationService.computeRetirementProjection(...)` par un appel à `RetirementCalculationService` (via `RetirementInputFactory`), puis transformation du résultat en flux annuel/mensuel.
- Idem pour `OverviewCalculationService.computeRetirementProjection(...)`.
- Supprimer les deux implémentations dupliquées une fois les tests de non-régression (snapshots RF-000) verts.
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-103 — Retraite - Tests de composant + garde-fou

- **Domaine** : Retraite
- **Prérequis** : RF-102
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `03-domaine-retraite.md`
- **Modifications attendues** :
- Tests unitaires sur `RetirementCalculationService` utilisant uniquement `RetirementCalculationInput` (aucun `BudgetDataModel`, aucun contexte Spring).
- Retirer le domaine Retraite de la liste des violations gelées dans la règle ArchUnit de RF-001.
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-200 — Fiscalité - Contrats (version large)

- **Domaine** : Fiscalité
- **Prérequis** : RF-001
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `04-domaine-fiscalite.md`
- **Modifications attendues** :
- Créer `TaxCalculationInput`, `TaxSimulationPeriod`, `AnnualTaxIncome`, `TaxHouseholdParameters` et les types associés (brackets/overrides).
- Créer `TaxInputFactory` (version 1, qui peut encore s'appuyer sur `findEarliestYear(BudgetDataModel)` en interne — c'est la première des deux étapes logiques décrites dans le fichier de domaine).
- **Statut** :
- [ ] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [x] Terminé
- [ ] Constaté comme mergé

### RF-201 — Fiscalité - Branchement du moteur

- **Domaine** : Fiscalité
- **Prérequis** : RF-200
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `04-domaine-fiscalite.md`
- **Modifications attendues** :
- Brancher `TaxCalculator` sur `TaxCalculationInput` via la Factory.
- Encapsulation de frontière uniquement : le contenu de l'Input peut rester large à ce stade (cf. RF-202 pour la réduction).
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-202 — Fiscalité - Réduction (sortie de findEarliestYear et de la projection retraite)

- **Domaine** : Fiscalité
- **Prérequis** : RF-201, RF-102
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `04-domaine-fiscalite.md`
- **Modifications attendues** :
- Faire consommer à `TaxCalculationInput` un `AnnualTaxableRetirementIncome` produit par `RetirementCalculationService` (RF-102), au lieu de reconstruire la pension en interne.
- Sortir `findEarliestYear` du calcul fiscal : la période de simulation est désormais déduite en amont (application) et passée explicitement via `TaxSimulationPeriod`.
- Retirer de `TaxCalculationInput` les champs devenus inutiles : `charges`, `placements`, `oneoff`, `transfers`, `bankImport`, `settings.pivotDate`, `settings.inflationRate` — à vérifier au cas par cas, pas supposé acquis à l'avance.
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-203 — Fiscalité - Tests de composant + garde-fou

- **Domaine** : Fiscalité
- **Prérequis** : RF-202
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `04-domaine-fiscalite.md`
- **Modifications attendues** :
- Tests unitaires sur `TaxCalculator` utilisant uniquement `TaxCalculationInput` réduit.
- Retirer le domaine Fiscalité de la liste des violations gelées ArchUnit.
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-300 — Patrimoine - Contrats

- **Domaine** : Patrimoine
- **Prérequis** : RF-001
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `05-domaine-patrimoine.md`
- **Modifications attendues** :
- Créer `PatrimoineProjectionInput`, `PlacementProjectionInput`, `CashflowProjection`, `PatrimoineProjectionParameters` et la Factory associée.
- Se limiter, à ce stade, aux champs de configuration statiques par placement (`sweepPriority`, `sweepCap`, `pauseTriggerBalance`, `pausePriority`) sans introduire `ContributionDecisionPlan` (voir le point ouvert du fichier de domaine, traité en RF-400).
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-301 — Patrimoine - Séparation des deux moteurs et branchement

- **Domaine** : Patrimoine
- **Prérequis** : RF-300
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `05-domaine-patrimoine.md`
- **Modifications attendues** :
- Séparer explicitement le moteur de projection patrimoniale du moteur de règles d'allocation/pause/sweep (aujourd'hui mêlés dans `PatrimoineServiceImpl`).
- Brancher `PatrimoineServiceImpl` sur les deux moteurs via la Factory ; le contrôleur REST devient une façade mince.
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-302 — Patrimoine - Tests de composant + garde-fou

- **Domaine** : Patrimoine
- **Prérequis** : RF-301
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `05-domaine-patrimoine.md`
- **Modifications attendues** :
- Tests unitaires sur les deux moteurs patrimoniaux utilisant uniquement les Inputs dédiés.
- Retirer le domaine Patrimoine de la liste des violations gelées ArchUnit.
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-400 — Trésorerie - Contrats (et décision du point ouvert Patrimoine/Trésorerie)

- **Domaine** : Trésorerie
- **Prérequis** : RF-202, RF-102, RF-301
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `06-domaine-tresorerie.md`, `05-domaine-patrimoine.md`
- **Modifications attendues** :
- PRÉALABLE OBLIGATOIRE : trancher explicitement, en revue avant d'écrire le code, la relation Patrimoine ↔ Trésorerie documentée comme point ouvert dans `05-domaine-patrimoine.md#point-ouvert` (introduction ou non d'un `ContributionDecisionPlan` produit par Trésorerie et consommé par Patrimoine, sans créer de cycle). Ne pas coder avant validation de la décision par Bulldo.
- Créer `TreasuryProjectionInput` et ses sous-types (`IncomeProjectionInput`, `ChargeProjectionInput`, `VariableIncomeProjection`, `OneOffCashflow`, `TransferProjection`, `PlacementCashflowInput`, `TaxProjection`, `RetirementIncomeProjection`, `TreasuryParameters`).
- Créer la Factory assemblant les projections de Fiscalité (RF-202), Retraite (RF-102) et Patrimoine (RF-301).
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-401 — Trésorerie - Branchement et extraction des fonctions unitaires

- **Domaine** : Trésorerie
- **Prérequis** : RF-400
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `06-domaine-tresorerie.md`
- **Modifications attendues** :
- Brancher `TresorerieCalculationService` sur `TreasuryProjectionInput`.
- Extraire `chargeMonthlyForYear`, `chargeAnnualForYear`, `incomeMonthlyForYear`, `incomeAnnualForYear` en fonctions de domaine pures prenant uniquement leur modèle minimal.
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-402 — Trésorerie - Tests de composant + garde-fou

- **Domaine** : Trésorerie
- **Prérequis** : RF-401
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `06-domaine-tresorerie.md`
- **Modifications attendues** :
- Tests unitaires sur `TresorerieCalculationService` utilisant uniquement `TreasuryProjectionInput`.
- Retirer le domaine Trésorerie de la liste des violations gelées ArchUnit.
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-500 — Pointage - Contrats

- **Domaine** : Banque / Pointage
- **Prérequis** : RF-001
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `07-domaine-banque-pointage.md`
- **Modifications attendues** :
- Créer `PointageInput` et le type partagé `BudgetLineProjection` (réutilisé ensuite par Analyse, RF-600).
- Peut démarrer tôt, en parallèle des domaines précédents : `BankImportCalculator` est déjà un calculateur pur et n'a pas besoin d'un `BankImportInput` global (à ne pas introduire).
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-501 — Pointage - Branchement

- **Domaine** : Banque / Pointage
- **Prérequis** : RF-500
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `07-domaine-banque-pointage.md`
- **Modifications attendues** :
- Brancher `PointageServiceImpl`/`PointageCalculator` sur `PointageInput`, en sortant `IncomeModel`/`ChargeModel`/`PlacementModel`/`SettingsModel` du calcul de pointage.
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-502 — Pointage - Tests de composant + garde-fou

- **Domaine** : Banque / Pointage
- **Prérequis** : RF-501
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `07-domaine-banque-pointage.md`
- **Modifications attendues** :
- Tests unitaires sur `PointageCalculator` utilisant uniquement `PointageInput`.
- Retirer le domaine Pointage de la liste des violations gelées ArchUnit (Import bancaire est déjà conforme, aucune action nécessaire de ce côté).
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-600 — Analyse - Contrats

- **Domaine** : Analyse
- **Prérequis** : RF-500
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `08-domaine-analyse.md`
- **Modifications attendues** :
- Créer `AnalyseInput`, en réutilisant `BudgetLineProjection` défini en RF-500.
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-601 — Analyse - Branchement et suppression de la fuite de résultat

- **Domaine** : Analyse
- **Prérequis** : RF-600
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `08-domaine-analyse.md`
- **Modifications attendues** :
- Brancher `AnalyseCalculator` sur `AnalyseInput` au lieu de `computeAnalyse(BudgetDataModel, BankImportModel, Integer)`.
- Supprimer le champ `BudgetDataModel data` de `AnalyseResultModel`, après vérification de tous les consommateurs actuels de ce champ (front compris).
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-602 — Analyse - Tests de composant + garde-fou

- **Domaine** : Analyse
- **Prérequis** : RF-601
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `08-domaine-analyse.md`
- **Modifications attendues** :
- Tests unitaires sur `AnalyseCalculator` utilisant uniquement `AnalyseInput`.
- Retirer le domaine Analyse de la liste des violations gelées ArchUnit.
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-700 — Objectifs - Contrat et ownership des settings

- **Domaine** : Objectifs
- **Prérequis** : RF-001
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `09-domaine-objectifs-notifications.md`, `12-settings.md`
- **Modifications attendues** :
- Créer le contrat `PlacementBalanceSnapshot(placementId, balance)` pour éviter une dépendance forte Objectifs → Patrimoine.
- Déplacer `goalSecureHorizonMonths` et `goalLiquidHorizonMonths` de `SettingsModel` vers les paramètres du domaine Objectifs.
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-701 — Notifications - Contrats (trois entrées distinctes)

- **Domaine** : Notifications
- **Prérequis** : RF-700
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `09-domaine-objectifs-notifications.md`
- **Modifications attendues** :
- Créer `DebitThresholdInput`, `BalanceFloorInput`, `ObjectifReachableInput` — explicitement trois entrées, jamais un `NotificationEvaluationInput` unique regroupant tous les domaines.
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-702 — Notifications - Branchement et suppression de NotificationContext

- **Domaine** : Notifications
- **Prérequis** : RF-701
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `09-domaine-objectifs-notifications.md`
- **Modifications attendues** :
- Brancher `DebitThresholdRule`, `BalanceFloorRule`, `ObjectifReachableRule` sur leurs Inputs respectifs.
- Supprimer `NotificationContext(BudgetDataModel, NotificationSettingsParameters)` ; l'assemblage des trois snapshots se fait désormais en amont de l'évaluation des règles, dans `NotificationDispatchService`.
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-703 — Notifications - Tests de composant + garde-fou

- **Domaine** : Notifications
- **Prérequis** : RF-702
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `09-domaine-objectifs-notifications.md`
- **Modifications attendues** :
- Un test unitaire par règle, construit avec quelques records seulement (sans budget complet).
- Retirer le domaine Notifications de la liste des violations gelées ArchUnit.
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-800 — Prêts - Contrats

- **Domaine** : Crédit
- **Prérequis** : RF-001
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `10-domaine-prets-suggestions.md`
- **Modifications attendues** :
- Créer `LoanAdviceInput`, `LoanInput`, `LiquidPlacementAlternative`. La résolution du bucket d'actif (aujourd'hui via `PlacementModel`/`AssetCategoryModel`) est faite par l'appelant, pas par le moteur.
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-801 — Prêts - Branchement

- **Domaine** : Crédit
- **Prérequis** : RF-800
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `10-domaine-prets-suggestions.md`
- **Modifications attendues** :
- Brancher `LoanAdviceCalculationService` et l'orchestration REST sur `LoanAdviceInput`.
- Encapsulation de frontière uniquement : ne pas sur-refactorer un moteur déjà largement pur (avertissement explicite du document de domaine).
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-802 — Suggestions de taux - Contrat et branchement

- **Domaine** : Crédit
- **Prérequis** : RF-001
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `10-domaine-prets-suggestions.md`
- **Modifications attendues** :
- Créer `PlacementRateSuggestionInput` et brancher `PlacementRateSuggestionService` dessus.
- Décider explicitement (et documenter la décision) si `AssetBucketResolver` reste dans Patrimoine ou devient un service applicatif — ne doit pas devenir une dépendance commune « pratique » de tous les modules.
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-803 — Prêts / Suggestions - Tests de composant + garde-fou

- **Domaine** : Crédit
- **Prérequis** : RF-801, RF-802
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `10-domaine-prets-suggestions.md`
- **Modifications attendues** :
- Tests unitaires sur les deux moteurs utilisant uniquement leurs Inputs dédiés.
- Retirer le domaine Crédit de la liste des violations gelées ArchUnit.
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-900 — Overview - Contrat OverviewInput

- **Domaine** : Overview
- **Prérequis** : RF-102, RF-202, RF-301, RF-401
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `11-domaine-overview.md`
- **Modifications attendues** :
- Créer `OverviewInput` assemblant `TreasuryProjection`, `PatrimoineProjection`, `RetirementProjection`, `TaxProjection`, `RealEstateProjection` déjà produites par les domaines respectifs.
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-901 — Overview - Branchement et suppression de la fuite de résultat

- **Domaine** : Overview
- **Prérequis** : RF-900
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `11-domaine-overview.md`
- **Modifications attendues** :
- Brancher `OverviewCalculationService` sur `OverviewInput` ; sa responsabilité devient uniquement la composition/transformation de projections déjà calculées.
- Supprimer le champ `BudgetDataModel data` de `OverviewResultModel`.
- Vérifier qu'aucune logique de recalcul (retraite ou autre) ne subsiste dans cette classe.
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-902 — Overview - Tests de composant + garde-fou

- **Domaine** : Overview
- **Prérequis** : RF-901
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `11-domaine-overview.md`
- **Modifications attendues** :
- Tests unitaires sur `OverviewCalculationService` utilisant uniquement `OverviewInput`.
- Retirer le domaine Overview de la liste des violations gelées ArchUnit — à ce stade, la règle ne devrait plus geler aucune violation.
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-A00 — Mutations - CommandServices par domaine

- **Domaine** : Transverse
- **Prérequis** : RF-103, RF-203, RF-302, RF-402, RF-502, RF-602, RF-703, RF-803, RF-902
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `01-sequencement.md`
- **Modifications attendues** :
- Introduire une façade typée par domaine (`PatrimoineCommandService`, `RetirementCommandService`, `TaxCommandService`, `BankImportCommandService`, `LoanCommandService`, `GoalCommandService`, `NotificationSettingsService`) au-dessus des mutations génériques existantes de `PersistenceManager`.
- Peut être scindé en un patch par domaine si le volume est trop important pour un seul agent ; l'ordre entre ces sous-patchs est libre, ils sont indépendants entre eux.
- Ne pas encore supprimer le code existant de `PersistenceManager` (`listKey`/`field`/`value`) — remplacement progressif des appelants uniquement.
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-B00 — Persistance palier 1 - Ports de lecture par domaine

- **Domaine** : Persistance
- **Prérequis** : RF-A00
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `13-persistance.md`
- **Modifications attendues** :
- Introduire les interfaces `budgetReader`, `patrimoineReader`, `retirementReader`, `taxReader`, `bankReader`, `loanReader`, `goalReader`, `settingsReader`.
- Leur implémentation peut, à ce stade, continuer à déléguer à `PersistenceManager`/`BudgetDataModel` en interne — ce n'est PAS un préalable à la séparation des entités JPA (palier 2, hors périmètre de cette liste, voir `13-persistance.md`).
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-B01 — Persistance palier 1 - Branchement des moteurs sur les ports

- **Domaine** : Persistance
- **Prérequis** : RF-B00
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `13-persistance.md`
- **Modifications attendues** :
- Remplacer les appels directs à `persistenceManager.getBudgetData()` dans les couches application par les ports de lecture dédiés.
- Peut être scindé par domaine si nécessaire.
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-C00 — OpenAPI - Split par domaine

- **Domaine** : Transverse
- **Prérequis** : RF-B01
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `01-sequencement.md`
- **Modifications attendues** :
- Scinder `openapi.yaml` selon les tags/capabilities définis par domaine, une fois leurs contrats stabilisés.
- Vérifier que les contrats composites hérités (ex. `/settings`) restent des façades de composition explicites (voir `12-settings.md`), pas des DTO qui recréent un modèle global.
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé

### RF-D00 — Point d'arrêt - Revue de la checklist Maven

- **Domaine** : Transverse
- **Prérequis** : RF-C00
- **Fichiers `.md` additionnels à lire** (en plus de `00-principes.md` et `01-sequencement.md`, toujours requis) : `14-checklist-maven.md`
- **Modifications attendues** :
- Revue de bout en bout : cocher chaque item de `14-checklist-maven.md` (uniquement palier 1 de persistance).
- Aucune modification de code attendue ici — c'est une revue humaine avant de planifier la création effective des modules Maven, qui fera l'objet d'une nouvelle liste de patchs.
- **Statut** :
- [x] Non commencé
- [ ] Démarré
- [ ] En attente de réponse
- [ ] Annulé
- [ ] Terminé
- [ ] Constaté comme mergé
