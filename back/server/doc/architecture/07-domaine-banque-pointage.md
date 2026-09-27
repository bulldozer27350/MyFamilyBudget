# 07 — Banque / Import bancaire / Pointage

Statut : 🟡 à valider

## Import bancaire — déjà mature

`BankImportCalculator` est un calculateur pur, sans Spring, sans DTO OpenAPI et sans persistance
directe, réutilisé par plusieurs entrées (CSV manuel, Enable Banking, opérations en cours,
catégorisation, rapprochement automatique). Cette mutualisation doit être conservée telle quelle.

`BankImportModel` regroupe cependant plusieurs sous-domaines (configuration d'import, catégories,
règles, transactions, opérations en cours, rapprochements) dont chaque opération n'a besoin que
d'une partie. Le pattern est déjà appliqué à l'échelle de chaque opération (les méthodes prennent
des listes ciblées) — **il ne faut pas introduire artificiellement un `BankImportInput` global**,
juste continuer à cibler des projections par cas d'usage.

## Pointage — frontière à améliorer

`PointageModel` agrège aujourd'hui plusieurs domaines directement :

```java
PointageModel(transactions, categories, matchings, charges, incomes, placements, settings)
```

Cible :

```java
public record PointageInput(
    List<BankTransaction> transactions,
    List<MatchingLink> matchings,
    List<BudgetLineProjection> activeBudgetLines,
    PointagePeriod period
) {}
```

`Pointage` ne connaît alors plus directement `IncomeModel`, `ChargeModel`, `PlacementModel` ni
`SettingsModel` — le calcul des lignes budgétaires actives devient une étape de composition en
amont (partagée avec Analyse, voir [08-domaine-analyse.md](08-domaine-analyse.md)).
