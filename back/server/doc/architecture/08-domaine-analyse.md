# 08 — Analyse

Statut : 🟡 à valider

## Constat

`AnalyseCalculator` est déjà une classe métier pure — bonne première frontière. Sa méthode
principale reste cependant `computeAnalyse(BudgetDataModel data, BankImportModel bankImport,
Integer monthsBack)` : double agrégation de modèle. C'est un use case de comparaison plutôt qu'un
domaine source.

## Contrat d'entrée

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
