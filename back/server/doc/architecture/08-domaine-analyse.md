# 08 — Analyse

Statut : 🟡 à valider

## Constat

`AnalyseCalculator` est déjà une classe métier pure — bonne première frontière. Sa méthode
principale reste cependant `computeAnalyse(BudgetDataModel data, BankImportModel bankImport,
Integer monthsBack)` : double agrégation de modèle. C'est un use case de comparaison plutôt qu'un
domaine source.

## Contrat d'entrée initial (insuffisant — voir l'évolution proposée plus bas)

```java
public record AnalyseInput(
    AnalysisPeriod period,
    List<BankTransaction> transactions,
    List<MatchingLink> matchings,
    List<BudgetLineProjection> budgetLines,
    List<PlacementPerformanceSnapshot> placements
) {}
```

Le point clé est `BudgetLineProjection` : Analyse ne doit pas savoir qu'une ligne vient de
`ChargeModel`, `IncomeModel` ou `PlacementModel`.

## Fuite par le résultat à corriger

`AnalyseResultModel` contient aujourd'hui un champ `BudgetDataModel data` en plus de ses
propriétés déjà calculées — c'est une dépendance inverse à éliminer (voir la règle générale dans
[00-principes.md](00-principes.md)). Cible :

```java
public record AnalyseResultModel(
    AnalyseKpiModel kpis,
    List<AnalyseLandingRowModel> landingData,
    List<AnalyseDriftRowModel> driftRows,
    List<AnalyseMonthlyCompareModel> monthlyCompareData,
    List<AnalyseCategorySummaryModel> categorySummaries,
    String currentMonthISO,
    String currentMonthLabel
) {}
```

Le champ `data` est un candidat clair à supprimer, après vérification de tous les consommateurs
actuels de ce champ (front compris).

## Évolution proposée du contrat d'entrée (RF-600) — 🟡 à valider

Le patch RF-600 est bloqué (statut « En attente de réponse » dans
[15-backlog-patchs.md](15-backlog-patchs.md)) tant que cette évolution n'est pas tranchée. Aucun
autre fichier du dossier ne décrit le contenu de `AnalyseInput` : `01-sequencement.md` et
`11-domaine-overview.md` ne font que le citer, `07-domaine-banque-pointage.md` définit
`BudgetLineProjection`, et `09-domaine-objectifs-notifications.md` (`PendingAmount`) est le seul
précédent de projection d'opérations en cours.

### Constat : ce que `AnalyseCalculator` consomme réellement

| Besoin du calcul | Source actuelle | Couvert par le contrat initial ? |
|---|---|---|
| Transactions de la période (coupure à `aujourd'hui - monthsBack`) | `BankImportModel.transactions` | oui (`transactions`) |
| Catégories : libellé, nature (`Revenu`), `compressible` — KPI, répartition par catégorie | `BankImportModel.categories` | **non** |
| Opérations en cours (`pending`) ajoutées au réel du mois courant | `BankImportModel.pendingOperations` | **non** |
| Rapprochements de **tous** les mois (comparatif mensuel, moyennes 3/12 mois) | `BankImportModel.matchings` | partiellement (`matchings` sans notion de mois) |
| Lignes budgétaires du mois courant (atterrissage, dérives) | `PointageInputFactory.activeBudgetLines`, appelé sur `BudgetDataModel` | oui (`budgetLines`) |
| Lignes budgétaires de **chacun des 24 derniers mois** (budget mensuel du comparatif) | idem, appelé dans une boucle | **non** |
| Nature (charge / revenu / placement) d'une ligne, y compris hors période active | identifiants des charges, revenus et placements | **non** |
| Date du jour (coupure, mois courant, moyennes) | `LocalDate.now()` / `YearMonth.now()` dans le calculateur | **non** |
| Performance des placements | — (aucun usage côté Java : les placements ne servent qu'à connaître la nature d'une ligne ; la détection de placements sous-performants est calculée côté JS) | `PlacementPerformanceSnapshot` sans consommateur |

### Contrat proposé

```java
public record AnalyseInput(
    AnalysisPeriod period,
    List<BankImportModel.BankTransactionModel> transactions,
    List<BankImportModel.CategoryModel> categories,
    List<BankImportModel.MatchingModel> matchings,            // tous les mois
    List<BankImportModel.PendingOperationModel> pendingOperations,
    List<MonthlyBudgetLines> monthlyBudgetLines,              // mois courant + fenêtre (<= 24 mois)
    List<BudgetLineKind> lineKinds                            // nature de toutes les lignes connues
) {}

public record AnalysisPeriod(LocalDate today, int monthsBack) {}
public record MonthlyBudgetLines(String monthISO, List<BudgetLineProjection> lines) {}
public record BudgetLineKind(String lineId, String kind) {}
```

Choix de conception associés :

- **Pas de `PlacementPerformanceSnapshot`.** Aucun consommateur Java ; le créer obligerait la Factory
  à inventer une donnée sans usage. S'il devient nécessaire (couches 2 et 3 de l'analyse
  budgétaire), il sera ajouté par le patch qui introduit ce besoin.
- **`AnalysisPeriod` porte la date du jour** au lieu de laisser le calculateur appeler
  `LocalDate.now()` : le moteur devient déterministe et testable sans dépendre de l'horloge.
  La Factory passe `LocalDate.now()` ; la sémantique de `monthsBack` est conservée (0 = tout
  l'historique, 12 par défaut, fenêtre du comparatif plafonnée à 24 mois).
- **Lignes du mois courant** : ce sont les lignes de `monthlyBudgetLines` dont `monthISO` est le mois
  de `period.today()` (toujours présent : la fenêtre compte au moins un mois). Pas de champ
  `budgetLines` séparé, pour ne pas porter deux fois la même information.
- **Composition en amont** : la Factory d'Analyse appelle `PointageInputFactory.activeBudgetLines`
  (RF-501) pour chaque mois de la fenêtre ; `ChargeModel`, `IncomeModel`, `PlacementModel` et
  `SettingsModel` ne sont plus connus du calculateur.
- **Types d'éléments réutilisés** de `BankImportModel` (transactions, catégories, rapprochements,
  opérations en cours), comme `PointageInput` (RF-500) : `BankImportModel` lui-même n'est jamais
  passé, et aucun `BankImportInput` global n'est introduit (voir
  [07-domaine-banque-pointage.md](07-domaine-banque-pointage.md)).
- **Point d'attention à conserver** : dans le calcul actuel, une nature est associée à un
  identifiant par écrasement successif charges → revenus → placements ; `BudgetLineKind` doit
  reproduire cet ordre pour ne pas changer les résultats.

### Options et conséquences

**Option A — étendre le contrat comme ci-dessus (recommandée).**

- RF-600 reste purement additif (quatre records, un test de normalisation, comme RF-500/RF-800).
- RF-601 branche `AnalyseCalculator` sans changement de résultat : tous les besoins sont couverts.
- RF-602 peut tester le moteur avec `AnalyseInput` seul, sans budget complet et sans horloge.
- Coût : sept composants au lieu de cinq. Chacun est une projection à propriétaire unique, sans
  `SettingsModel` ni modèle du budget : ce n'est pas un nouveau modèle global (voir
  [00-principes.md](00-principes.md)).

**Option B — conserver le contrat du document tel quel.**

- `AnalyseInput` ne permet pas de brancher `AnalyseCalculator` : RF-601 devrait soit continuer à
  recevoir `BankImportModel` et `BudgetDataModel` à côté de l'Input (l'objectif du chantier n'est
  alors pas atteint), soit supprimer catégories, opérations en cours et comparatif mensuel
  (changement de comportement, snapshots RF-000 en échec).
- Le contrat serait de toute façon complété dans RF-601, ce qui contredit la règle « un patch = un
  item » : RF-600 ne livrerait pas un contrat stable.
- `PlacementPerformanceSnapshot` serait livré sans consommateur, avec une forme non définie.

**Option C (écartée) — un `Input` par sortie** (KPI/catégories, atterrissage, comparatif mensuel,
dérives), sur le modèle des trois entrées de Notifications. Non retenue : `computeAnalyse` produit
un seul résultat, consommé par un seul endpoint ; la découpe multiplierait les contrats et
changerait le titre et le périmètre de RF-601 sans gain de testabilité proportionné.

### Impact sur les autres patchs

- **RF-601** : inchangé dans son objectif ; la suppression de `AnalyseResultModel.data` doit
  vérifier le consommateur front `view/js/views/analyse-view.js` (`apiData?.data || data`).
- **RF-602** : les tests s'écrivent sur `AnalyseInput` uniquement.
- **RF-900** : aucun impact (Overview ne consomme pas `AnalyseInput`).
