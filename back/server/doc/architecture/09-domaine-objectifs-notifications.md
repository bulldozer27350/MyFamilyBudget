# 09 — Objectifs et Notifications

Statut : 🟡 à valider

## Objectifs

Les objectifs n'ont pas de tag OpenAPI propre aujourd'hui (exposés via Analyse et les données
globales), ce qui ne signifie pas qu'ils doivent être absorbés dans Analyse : le modèle montre un
sous-domaine cohérent (`SavingsGoal` : target, targetDate, allocations, notes).

- **Relation avec Patrimoine** : une allocation d'objectif pointe vers un placement par
  identifiant ; le domaine Objectifs n'a pas besoin d'importer `PlacementModel` complet, un
  contrat `PlacementBalanceSnapshot(placementId, balance)` suffit.
- **Relation avec Settings** : `goalSecureHorizonMonths` et `goalLiquidHorizonMonths` doivent être
  déplacés dans les paramètres du domaine Objectifs (voir [12-settings.md](12-settings.md)),
  pas laissés dans `SettingsModel`.

## Notifications — le meilleur exemple du principe « un contexte métier doit être petit »

Contexte actuel : `NotificationContext(BudgetDataModel data, NotificationSettingsParameters
settings)` — déjà mieux qu'un accès direct à la persistance, mais encore beaucoup trop large.

**Il ne faut surtout pas créer** un `NotificationEvaluationInput` unique regroupant
incomes/charges/placements/loans/retirement/bank — ce serait le même anti-pattern sous un autre
nom. Trois entrées distinctes, une par règle :

```java
public record DebitThresholdInput(
    BigDecimal threshold,
    List<Transaction> recentTransactions // id, date, label, amount
) {}

public record BalanceFloorInput(
    BigDecimal floor,
    BigDecimal openingBalance,
    List<AccountTransactionAmount> importedTransactions,
    List<PendingAmount> pendingOperations // status, amount
) {}

public record ObjectifReachableInput(
    List<GoalCoverage> goals, // id, label, targetAmount, allocations(placementId, amount)
    List<PlacementBalance> placementBalances // placementId, balance
) {}
```

`NotificationDispatchService` reste responsable de l'activation, des plages silencieuses, de la
déduplication et des canaux — mais ne construit plus et ne connaît plus `BudgetDataModel`.
L'assemblage des trois snapshots doit être effectué avant l'évaluation des règles, en amont.

Chaque règle devient alors testable avec quelques records, sans budget complet — préférable à des
tests de règles qui dépendent implicitement de dizaines de valeurs non pertinentes.
