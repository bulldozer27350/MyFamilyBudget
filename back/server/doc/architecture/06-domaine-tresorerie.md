# 06 — Trésorerie

Statut : 🟡 à valider

## Constat

`TresorerieCalculationService` est déjà extrait du contrôleur, mais reçoit encore directement
`BudgetDataModel` : `computeTresorerie(BudgetDataModel data, boolean useConstantEuros)`.
L'extraction hors contrôleur était nécessaire mais ne constitue pas encore la frontière de
domaine finale.

## Le bon découpage n'est pas un Input unique

Un modèle d'entrée unique deviendrait rapidement un `BudgetDataModel` bis, vu le nombre de
familles consultées aujourd'hui (settings, revenus, charges, oneoff, variables, placements,
virements, fiscalité, retraite, import bancaire). Le principe à retenir : Trésorerie reçoit **les
résultats/projections déjà calculés des autres domaines**, jamais leurs modèles source.

```java
public record TreasuryProjectionInput(
    SimulationPeriod period,
    List<IncomeProjectionInput> incomes,
    List<ChargeProjectionInput> charges,
    List<VariableIncomeProjection> variableIncomes,
    List<OneOffCashflow> oneOffExpenses,
    List<TransferProjection> transfers,
    List<PlacementCashflowInput> placements,
    TaxProjection taxProjection,
    RetirementIncomeProjection retirementIncome,
    TreasuryParameters parameters
) {}
```

```text
Fiscalité ────────→ TaxProjection
Retraite ─────────→ RetirementIncomeProjection
Patrimoine ───────→ PlacementCashflowInput / projection
Banque ───────────→ RealAverageSnapshot
                         ↓
                 TreasuryCalculationService
```

Voir le point ouvert dans [05-domaine-patrimoine.md](05-domaine-patrimoine.md#point-ouvert) sur la
relation retour éventuelle Trésorerie → Patrimoine (décision de contribution) : à trancher au
moment de cette étape, pas avant.

## Fonctions de calcul unitaires déjà publiques

`chargeMonthlyForYear`, `chargeAnnualForYear`, `incomeMonthlyForYear`, `incomeAnnualForYear` sont
de bons candidats pour devenir de vraies fonctions de domaine prenant uniquement leur modèle
minimal — elles ne doivent pas recevoir `BudgetDataModel`.

## Conclusion

```text
BudgetDataModel
    ↓ application/composition
    ├── projection fiscale
    ├── projection retraite
    ├── projection placements
    ├── données bancaires nécessaires
    └── lignes revenu/charge normalisées
             ↓
      TreasuryProjectionInput
             ↓
      TreasuryCalculationService
```

Trésorerie devient un agrégateur de **projections**, pas un agrégateur de modèles de persistance.
